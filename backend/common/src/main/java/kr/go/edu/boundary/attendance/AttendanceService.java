package kr.go.edu.boundary.attendance;

import java.security.SecureRandom;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import kr.go.edu.boundary.common.AuditWriter;
import kr.go.edu.boundary.common.Digests;
import kr.go.edu.boundary.signing.SignatureImages;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AttendanceService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final JdbcTemplate jdbc;
    private final AuditWriter audit;
    private final String publicBaseUrl;

    public AttendanceService(JdbcTemplate jdbc, AuditWriter audit,
            @Value("${app.public-base-url}") String publicBaseUrl) {
        this.jdbc = jdbc;
        this.audit = audit;
        this.publicBaseUrl = publicBaseUrl.replaceAll("/+$", "");
    }

    @Transactional
    public Invitation create(CreateEvent request, String actor) {
        UUID id = UUID.randomUUID();
        String token = token();
        jdbc.update("INSERT INTO attendance_events(id, title, event_date, token_hash) VALUES (?, ?, ?, ?)",
                id, request.title().strip(), request.eventDate(), Digests.sha256(token));
        audit.record(actor, "ATTENDANCE_EVENT_CREATED", "ATTENDANCE_EVENT", id, Map.of());
        return invitation(id, token);
    }

    @Transactional
    public Invitation renew(UUID id, String actor) {
        String token = token();
        int updated = jdbc.update("UPDATE attendance_events SET token_hash = ? WHERE id = ? AND closed_at IS NULL",
                Digests.sha256(token), id);
        if (updated == 0) {
            throw new IllegalStateException("접수 중인 행사에서만 링크를 다시 발급할 수 있습니다.");
        }
        audit.record(actor, "ATTENDANCE_LINK_RENEWED", "ATTENDANCE_EVENT", id, Map.of());
        return invitation(id, token);
    }

    @Transactional
    public void close(UUID id, String actor) {
        int updated = jdbc.update("UPDATE attendance_events SET closed_at = now() WHERE id = ? AND closed_at IS NULL", id);
        if (updated == 0) {
            throw new IllegalStateException("접수 중인 행사를 찾을 수 없습니다.");
        }
        audit.record(actor, "ATTENDANCE_EVENT_CLOSED", "ATTENDANCE_EVENT", id, Map.of());
    }

    public List<EventSummary> events() {
        return jdbc.query("""
                SELECT e.id, e.title, e.event_date, e.closed_at, count(a.id) AS total
                FROM attendance_events e LEFT JOIN attendance_entries a ON a.event_id = e.id
                GROUP BY e.id ORDER BY e.event_date DESC, e.created_at DESC, e.id
                """, (result, row) -> new EventSummary(event(result), result.getLong("total")));
    }

    public Event event(UUID id) {
        return jdbc.query("SELECT id, title, event_date, closed_at FROM attendance_events WHERE id = ?",
                (result, row) -> event(result), id).stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("행사를 찾을 수 없습니다."));
    }

    public List<Entry> entries(UUID eventId) {
        event(eventId);
        return jdbc.query("""
                SELECT id, education_office, job_title, full_name, phone, signed_at
                FROM attendance_entries WHERE event_id = ? ORDER BY signed_at, id
                """, (result, row) -> entry(result), eventId);
    }

    public byte[] image(UUID eventId, UUID entryId) {
        return jdbc.query("SELECT image_png FROM attendance_entries WHERE event_id = ? AND id = ?",
                (result, row) -> result.getBytes("image_png"), eventId, entryId).stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("서명 이미지를 찾을 수 없습니다."));
    }

    public PublicEvent lookup(String token) {
        Event event = invitedEvent(token, false);
        if (event.closedAt() != null) throw new IllegalStateException("출석 접수가 마감되었습니다.");
        return new PublicEvent(event.title(), event.eventDate());
    }

    @Transactional
    public Receipt submit(Submit request) {
        if (!request.consent()) throw new IllegalArgumentException("출석 정보와 서명 저장에 동의해 주세요.");
        String office = request.educationOffice().strip();
        String job = request.jobTitle().strip();
        String name = request.fullName().strip();
        String phone = normalizePhone(request.phone());
        byte[] png = SignatureImages.parsePng(request.pngBase64());
        String digest = Digests.sha256(png);

        // 마감과 제출이 겹치면 행 잠금으로 먼저 완료된 쪽을 따릅니다.
        Event event = invitedEvent(request.token(), true);
        List<Saved> previous = saved(event.id(), request.submissionId());
        if (!previous.isEmpty()) return retry(previous.get(0), office, job, name, phone, digest);
        if (event.closedAt() != null) throw new IllegalStateException("출석 접수가 마감되었습니다.");

        UUID entryId = UUID.randomUUID();
        List<Receipt> inserted = jdbc.query("""
                INSERT INTO attendance_entries(id, event_id, submission_id, education_office, job_title,
                    full_name, phone, image_png, image_sha256)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT DO NOTHING RETURNING id, signed_at
                """, (result, row) -> receipt(result), entryId, event.id(), request.submissionId(),
                office, job, name, phone, png, digest);
        if (!inserted.isEmpty()) {
            audit.record("방문자", "ATTENDANCE_SUBMITTED", "ATTENDANCE_ENTRY", entryId,
                    Map.of("eventId", event.id()));
            return inserted.get(0);
        }
        previous = saved(event.id(), request.submissionId());
        if (!previous.isEmpty()) return retry(previous.get(0), office, job, name, phone, digest);
        throw new IllegalStateException("이미 출석을 등록했습니다. 확인이 필요하면 담당자에게 문의해 주세요.");
    }

    List<ExportRow> exportRows(UUID eventId) {
        return jdbc.query("""
                SELECT id, education_office, job_title, full_name, phone, signed_at, image_png
                FROM attendance_entries WHERE event_id = ? ORDER BY signed_at, id
                """, (result, row) -> new ExportRow(entry(result), result.getBytes("image_png")), eventId);
    }

    private Event invitedEvent(String token, boolean lock) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) {
            throw new IllegalArgumentException("유효한 출석 링크를 입력해 주세요.");
        }
        String sql = "SELECT id, title, event_date, closed_at FROM attendance_events WHERE token_hash = ?";
        return jdbc.query(lock ? sql + " FOR SHARE" : sql, (result, row) -> event(result), Digests.sha256(token))
                .stream().findFirst().orElseThrow(() -> new IllegalArgumentException("출석 링크를 확인해 주세요."));
    }

    private List<Saved> saved(UUID eventId, UUID submissionId) {
        return jdbc.query("""
                SELECT id, signed_at, education_office, job_title, full_name, phone, image_sha256
                FROM attendance_entries WHERE event_id = ? AND submission_id = ?
                """, (result, row) -> new Saved(receipt(result), result.getString("education_office"),
                result.getString("job_title"), result.getString("full_name"), result.getString("phone"),
                result.getString("image_sha256")), eventId, submissionId);
    }

    private static Receipt retry(Saved saved, String office, String job, String name, String phone, String digest) {
        if (saved.office().equals(office) && saved.job().equals(job) && saved.name().equals(name)
                && saved.phone().equals(phone) && saved.digest().equals(digest)) return saved.receipt();
        throw new IllegalStateException("이 요청은 이미 처리되었습니다. 확인이 필요하면 담당자에게 문의해 주세요.");
    }

    static String normalizePhone(String value) {
        String normalized = value.strip().replaceAll("[\\s()\\-]", "");
        if (normalized.startsWith("+82")) normalized = "0" + normalized.substring(3);
        if (!normalized.matches("0[0-9]{8,10}|\\+[1-9][0-9]{7,14}")) {
            throw new IllegalArgumentException("전화번호 형식을 확인해 주세요.");
        }
        return normalized;
    }

    private Invitation invitation(UUID id, String token) {
        return new Invitation(event(id), publicBaseUrl + "/attendance/index.html#token=" + token);
    }

    private static String token() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static Event event(ResultSet result) throws SQLException {
        return new Event(result.getObject("id", UUID.class), result.getString("title"),
                result.getObject("event_date", LocalDate.class), result.getObject("closed_at", OffsetDateTime.class));
    }

    private static Entry entry(ResultSet result) throws SQLException {
        return new Entry(result.getObject("id", UUID.class), result.getString("education_office"),
                result.getString("job_title"), result.getString("full_name"), result.getString("phone"),
                result.getObject("signed_at", OffsetDateTime.class));
    }

    private static Receipt receipt(ResultSet result) throws SQLException {
        return new Receipt(result.getObject("id", UUID.class), result.getObject("signed_at", OffsetDateTime.class));
    }

    public record CreateEvent(@NotBlank @Size(max = 150) String title, @NotNull LocalDate eventDate) { }
    public record Submit(@NotBlank @Size(max = 43) String token, @NotNull UUID submissionId,
            @NotBlank @Size(max = 150) String educationOffice, @NotBlank @Size(max = 100) String jobTitle,
            @NotBlank @Size(max = 100) String fullName, @NotBlank @Size(max = 32) String phone,
            @NotBlank @Size(max = 400_000) String pngBase64, boolean consent) { }
    public record Event(UUID id, String title, LocalDate eventDate, OffsetDateTime closedAt) { }
    public record EventSummary(Event event, long total) { }
    public record Invitation(Event event, String url) { }
    public record PublicEvent(String title, LocalDate eventDate) { }
    public record Entry(UUID id, String educationOffice, String jobTitle, String fullName, String phone,
            OffsetDateTime signedAt) { }
    public record Receipt(UUID id, OffsetDateTime signedAt) { }
    record ExportRow(Entry entry, byte[] image) { }
    private record Saved(Receipt receipt, String office, String job, String name, String phone, String digest) { }
}
