package kr.go.edu.boundary.signing;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.SecureRandom;
import java.net.URI;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.imageio.ImageIO;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.go.edu.boundary.common.AuditWriter;
import kr.go.edu.boundary.common.Digests;
import kr.go.edu.boundary.common.OutboxWriter;
import kr.go.edu.boundary.security.CurrentActor;
import kr.go.edu.boundary.tasks.TaskRecord;
import kr.go.edu.boundary.tasks.TaskService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SigningService {
    private static final String CONSENT_TEXT = "위 방문 내역을 확인했으며, 서명 및 방문 내역의 저장에 동의합니다.";
    private static final String LOCATION_CONSENT_TEXT = "현장 위치의 확인과 저장에 동의합니다.";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final RowMapper<SessionRow> SESSION_MAPPER =
            (result, row) -> mapSession(result);
    private final JdbcTemplate jdbc;
    private final TaskService tasks;
    private final CurrentActor actor;
    private final AuditWriter audit;
    private final OutboxWriter outbox;
    private final ObjectMapper mapper;
    private final String publicBaseUrl;

    public SigningService(JdbcTemplate jdbc, TaskService tasks, CurrentActor actor,
            AuditWriter audit, OutboxWriter outbox, ObjectMapper mapper,
            @Value("${app.public-base-url}") String publicBaseUrl,
            @Value("${app.mode}") String mode) {
        this.jdbc = jdbc;
        this.tasks = tasks;
        this.actor = actor;
        this.audit = audit;
        this.outbox = outbox;
        this.mapper = mapper;
        URI address = URI.create(publicBaseUrl);
        boolean loopback = "local".equals(mode) && ("localhost".equals(address.getHost())
                || "127.0.0.1".equals(address.getHost()));
        if (address.getHost() == null || (!"https".equals(address.getScheme()) && !loopback)) {
            throw new IllegalArgumentException("태블릿 접속 주소 APP_PUBLIC_BASE_URL에는 HTTPS가 필요합니다.");
        }
        this.publicBaseUrl = publicBaseUrl.replaceAll("/+$", "");
    }

    @Transactional
    public IssuedSession issue(IssueRequest request, Authentication authentication) {
        TaskRecord task = tasks.get(request.taskId());
        String name = required(request.signerName(), "서명자 이름", 200);
        String affiliation = required(request.affiliation(), "소속", 200);
        Purpose purpose = parsePurpose(request.purpose());
        String token = newToken();
        UUID id = UUID.randomUUID();
        OffsetDateTime expiresAt = OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(10);
        // DB에는 링크 원문을 넣지 않습니다. 유출되더라도 해시만으로 서명할 수 없습니다.
        jdbc.update("INSERT INTO signing_sessions(id, task_id, token_hash, signer_name, "
                        + "signer_affiliation, purpose, task_version, issued_by, expires_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, task.id(), Digests.sha256(token), name, affiliation, purpose.name(), task.version(),
                actor.id(authentication), expiresAt);
        audit.record(authentication.getName(), "SIGNING_SESSION_ISSUED", "SIGNING_SESSION", id,
                Map.of("taskId", task.id().toString(), "purpose", purpose.name()));
        // URL fragment는 HTTP 요청 경로에 포함되지 않아 서버 접근 로그에 토큰이 남지 않습니다.
        String url = publicBaseUrl + "/user/index.html#token=" + token;
        return new IssuedSession(id, expiresAt, url, qrDataUrl(url));
    }

    public PublicSession lookup(String token) {
        SessionRow session = activeSession(token);
        TaskRecord task = tasks.get(session.taskId());
        verifyTaskVersion(task, session);
        return new PublicSession(task.applicationNo(), task.applicantName(), task.schoolName(), task.status(),
                session.signerName(), session.affiliation(), session.purpose(), session.expiresAt(),
                CONSENT_TEXT, LOCATION_CONSENT_TEXT);
    }

    // 링크 소비, 이미지 저장, 감사 기록, 아웃박스 기록을 한 트랜잭션으로 묶습니다.
    @Transactional
    public Receipt submit(SubmitRequest request) {
        if (!request.consent()) {
            throw new IllegalArgumentException("서명 동의 확인이 필요합니다.");
        }
        SessionRow session = claimSession(request.token());
        byte[] png = SignatureImages.parsePng(request.pngBase64());
        Location location = validateLocation(request);
        // 상태 변경과 서명 저장이 겹치면 잠금 순서에 따라 하나가 먼저 끝나게 합니다.
        jdbc.queryForObject("SELECT version FROM detail_tasks WHERE id = ? FOR SHARE", Long.class,
                session.taskId());
        TaskRecord task = tasks.get(session.taskId());
        verifyTaskVersion(task, session);
        String snapshotJson = snapshotJson(task);
        String snapshotHash = Digests.sha256(snapshotJson);
        String imageHash = Digests.sha256(png);
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO signatures(id, session_id, task_id, signer_name, signer_affiliation, "
                        + "purpose, image_png, image_sha256, task_snapshot_sha256, task_snapshot, "
                        + "consent_text, location_consent_text, "
                        + "device_label, client_signed_at, location_captured_at, latitude, longitude, "
                        + "accuracy_m, location_consent, location_status) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, session.id(), task.id(), session.signerName(), session.affiliation(),
                session.purpose(), png, imageHash, snapshotHash, snapshotJson,
                CONSENT_TEXT, LOCATION_CONSENT_TEXT,
                optional(request.deviceLabel(), 120), request.clientSignedAt(), request.locationCapturedAt(),
                location.latitude(), location.longitude(), location.accuracyM(),
                request.locationConsent(), location.status());
        audit.record(session.signerName(), "SIGNATURE_SUBMITTED", "SIGNATURE", id,
                Map.of("taskId", task.id().toString(), "locationStatus", location.status(),
                        "imageSha256", imageHash, "locationConsent", request.locationConsent()));
        // 아직 전송하지 않아도 같은 DB 트랜잭션에 전송 대기 기록을 남깁니다.
        outbox.record("SIGNATURE_CREATED", id, new SignatureSyncPayload(
                id, task.id(), session.id(), session.signerName(), session.affiliation(),
                session.purpose(), imageHash, snapshotHash, snapshotJson,
                CONSENT_TEXT, LOCATION_CONSENT_TEXT,
                Base64.getEncoder().encodeToString(png), request.locationConsent(), location.status(),
                location.latitude(), location.longitude(), location.accuracyM()));
        return new Receipt(id, task.id(), imageHash);
    }

    public List<SignatureSummary> list(UUID taskId) {
        tasks.get(taskId);
        return jdbc.query("SELECT id, signer_name, signer_affiliation, purpose, received_at, "
                        + "latitude, longitude, accuracy_m, location_status, image_sha256 "
                        + "FROM signatures WHERE task_id = ? ORDER BY received_at DESC",
                (result, row) -> new SignatureSummary(result.getObject("id", UUID.class),
                        result.getString("signer_name"), result.getString("signer_affiliation"),
                        result.getString("purpose"), result.getObject("received_at", OffsetDateTime.class),
                        (Double) result.getObject("latitude"), (Double) result.getObject("longitude"),
                        (Double) result.getObject("accuracy_m"), result.getString("location_status"),
                        result.getString("image_sha256")), taskId);
    }

    public byte[] image(UUID id) {
        return jdbc.query("SELECT image_png FROM signatures WHERE id = ?",
                (result, row) -> result.getBytes(1), id).stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("서명을 찾을 수 없습니다."));
    }

    public long count() {
        Long count = jdbc.queryForObject("SELECT count(*) FROM signatures", Long.class);
        return count == null ? 0 : count;
    }

    private SessionRow activeSession(String token) {
        return jdbc.query("SELECT id, task_id, signer_name, signer_affiliation, purpose, task_version, expires_at "
                        + "FROM signing_sessions WHERE token_hash = ? AND consumed_at IS NULL AND expires_at > now()",
                SESSION_MAPPER, tokenHash(token)).stream()
                .findFirst().orElseThrow(() -> new IllegalStateException("서명 링크가 만료되었거나 이미 사용되었습니다."));
    }

    private SessionRow claimSession(String token) {
        // UPDATE ... RETURNING은 동시에 들어온 두 요청 중 하나만 일회용 링크를 소비하게 합니다.
        List<SessionRow> claimed = jdbc.query("UPDATE signing_sessions SET consumed_at = now() "
                        + "WHERE token_hash = ? AND consumed_at IS NULL AND expires_at > now() "
                        + "RETURNING id, task_id, signer_name, signer_affiliation, purpose, task_version, expires_at",
                SESSION_MAPPER, tokenHash(token));
        if (claimed.isEmpty()) {
            throw new IllegalStateException("서명 링크가 만료되었거나 이미 사용되었습니다.");
        }
        return claimed.get(0);
    }

    private static SessionRow mapSession(ResultSet result) throws SQLException {
        return new SessionRow(result.getObject("id", UUID.class),
                result.getObject("task_id", UUID.class), result.getString("signer_name"),
                result.getString("signer_affiliation"), result.getString("purpose"),
                result.getLong("task_version"), result.getObject("expires_at", OffsetDateTime.class));
    }

    private static Purpose parsePurpose(String value) {
        try {
            return Purpose.valueOf(value);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("방문 목적이 올바르지 않습니다.");
        }
    }

    private static String newToken() {
        byte[] random = new byte[32];
        RANDOM.nextBytes(random);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(random);
    }

    private static String tokenHash(String token) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) {
            throw new IllegalArgumentException("잘못된 서명 링크입니다.");
        }
        return Digests.sha256(token);
    }

    private static void verifyTaskVersion(TaskRecord task, SessionRow session) {
        if (task.version() != session.taskVersion()) {
            throw new IllegalStateException("신청 내역이 변경되었습니다. 담당자에게 새 서명 링크를 요청하세요.");
        }
    }

    private String snapshotJson(TaskRecord task) {
        try {
            return mapper.writeValueAsString(new TaskSnapshot(task.id(), task.applicationNo(),
                    task.applicantName(), task.schoolName(), task.status(), task.version()));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("신청 내역 증적을 만들 수 없습니다.", exception);
        }
    }

    private static Location validateLocation(SubmitRequest request) {
        LocationStatus status;
        try {
            status = LocationStatus.valueOf(request.locationStatus());
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("위치 상태가 올바르지 않습니다.");
        }
        if (!request.locationConsent() && status != LocationStatus.NOT_REQUESTED) {
            throw new IllegalArgumentException("위치 동의 없이 위치 결과를 저장할 수 없습니다.");
        }
        if (status == LocationStatus.AVAILABLE) {
            if (request.latitude() == null || request.longitude() == null || request.accuracyM() == null
                    || !Double.isFinite(request.latitude()) || !Double.isFinite(request.longitude())
                    || !Double.isFinite(request.accuracyM()) || Math.abs(request.latitude()) > 90
                    || Math.abs(request.longitude()) > 180 || request.accuracyM() <= 0) {
                throw new IllegalArgumentException("위치 좌표 또는 정확도가 올바르지 않습니다.");
            }
            return new Location(status.name(), request.latitude(), request.longitude(), request.accuracyM());
        }
        return new Location(status.name(), null, null, null);
    }

    private static String required(String value, String label, int maxLength) {
        if (value == null || value.isBlank() || value.length() > maxLength) {
            throw new IllegalArgumentException(label + " 입력값이 올바르지 않습니다.");
        }
        return value.strip();
    }

    private static String optional(String value, int maxLength) {
        return value == null ? null : value.substring(0, Math.min(value.length(), maxLength)).strip();
    }

    private static String qrDataUrl(String value) {
        try {
            BitMatrix matrix = new QRCodeWriter().encode(value, BarcodeFormat.QR_CODE, 280, 280);
            BufferedImage image = new BufferedImage(matrix.getWidth(), matrix.getHeight(),
                    BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < matrix.getHeight(); y++) {
                for (int x = 0; x < matrix.getWidth(); x++) {
                    image.setRGB(x, y, matrix.get(x, y) ? 0x111827 : 0xFFFFFF);
                }
            }
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            ImageIO.write(image, "png", bytes);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes.toByteArray());
        } catch (WriterException | IOException exception) {
            throw new IllegalStateException("서명 QR 코드를 만들 수 없습니다.", exception);
        }
    }

    public record IssueRequest(UUID taskId, String signerName, String affiliation, String purpose) { }
    public record IssuedSession(UUID id, OffsetDateTime expiresAt, String url, String qrDataUrl) { }
    public record PublicSession(String applicationNo, String applicantName, String schoolName, String taskStatus,
            String signerName, String affiliation, String purpose, OffsetDateTime expiresAt,
            String consentText, String locationConsentText) { }
    public record SubmitRequest(String token, String pngBase64, boolean consent, boolean locationConsent,
            String deviceLabel,
            OffsetDateTime clientSignedAt, OffsetDateTime locationCapturedAt,
            String locationStatus, Double latitude, Double longitude,
            Double accuracyM) { }
    public record Receipt(UUID signatureId, UUID taskId, String imageSha256) { }
    public record SignatureSummary(UUID id, String signerName, String affiliation, String purpose,
            OffsetDateTime receivedAt, Double latitude, Double longitude, Double accuracyM,
            String locationStatus, String imageSha256) { }
    private record SessionRow(UUID id, UUID taskId, String signerName, String affiliation,
            String purpose, long taskVersion, OffsetDateTime expiresAt) { }
    private record TaskSnapshot(UUID taskId, String applicationNo, String applicantName,
            String schoolName, String status, long version) { }
    private record Location(String status, Double latitude, Double longitude, Double accuracyM) { }
    private record SignatureSyncPayload(UUID signatureId, UUID taskId, UUID sessionId,
            String signerName, String affiliation, String purpose, String imageSha256,
            String taskSnapshotSha256, String taskSnapshot, String consentText,
            String locationConsentText,
            String imagePngBase64, boolean locationConsent, String locationStatus,
            Double latitude, Double longitude, Double accuracyM) { }
    private enum Purpose { EDUCATION, REVIEW, OTHER }
    private enum LocationStatus { AVAILABLE, DENIED, UNAVAILABLE, TIMEOUT, NOT_REQUESTED }
}

