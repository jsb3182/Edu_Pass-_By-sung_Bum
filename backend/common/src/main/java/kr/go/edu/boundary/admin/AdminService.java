package kr.go.edu.boundary.admin;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import kr.go.edu.boundary.common.AuditWriter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminService {
    private static final RowMapper<UserSummary> USER_MAPPER =
            (result, row) -> new UserSummary(result.getObject("id", UUID.class),
                    result.getString("username"), result.getString("role"),
                    result.getBoolean("active"), result.getObject("created_at", OffsetDateTime.class));
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final AuditWriter audit;

    public AdminService(JdbcTemplate jdbc, PasswordEncoder encoder, AuditWriter audit) {
        this.jdbc = jdbc;
        this.encoder = encoder;
        this.audit = audit;
    }

    public Summary summary() {
        return new Summary(number("SELECT count(*) FROM detail_tasks"),
                number("SELECT count(*) FROM signatures"),
                number("SELECT count(*) FROM outbox_events WHERE exported_at IS NULL"),
                number("SELECT count(*) FROM import_batches"));
    }

    public List<AuditEntry> audit() {
        return jdbc.query("SELECT id, actor, action, entity_type, entity_id, occurred_at "
                        + "FROM audit_events ORDER BY occurred_at DESC LIMIT 100",
                (result, row) -> new AuditEntry(result.getObject("id", UUID.class),
                        result.getString("actor"), result.getString("action"),
                        result.getString("entity_type"), result.getObject("entity_id", UUID.class),
                        result.getObject("occurred_at", OffsetDateTime.class)));
    }

    public List<UserSummary> users() {
        return jdbc.query("SELECT id, username, role, active, created_at FROM users ORDER BY created_at",
                USER_MAPPER);
    }

    @Transactional
    public UserSummary createUser(String username, String password, String role, String actor) {
        if (username == null || !username.matches("[A-Za-z0-9._-]{3,120}")) {
            throw new IllegalArgumentException("계정명은 영문·숫자·점·밑줄·하이픈 3~120자로 입력하세요.");
        }
        if (password == null || password.length() < 16 || password.length() > 128) {
            throw new IllegalArgumentException("비밀번호는 16~128자로 입력하세요.");
        }
        if (!"ADMIN".equals(role) && !"OPERATOR".equals(role)) {
            throw new IllegalArgumentException("권한이 올바르지 않습니다.");
        }
        if (jdbc.queryForObject("SELECT count(*) FROM users WHERE username = ?", Long.class, username) > 0) {
            throw new IllegalStateException("이미 존재하는 계정명입니다.");
        }
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users(id, username, password_hash, role) VALUES (?, ?, ?, ?)",
                id, username, encoder.encode(password), role);
        audit.record(actor, "USER_CREATED", "USER", id, Map.of("username", username, "role", role));
        return user(id);
    }

    @Transactional
    public void deactivate(UUID id, String actor) {
        // 마지막 관리자 검사를 동시에 통과하는 두 요청이 생기지 않게 잠급니다.
        jdbc.queryForObject("SELECT pg_advisory_xact_lock(907252)", (result, row) -> result.getObject(1));
        UserSummary user = user(id);
        if (!user.active()) {
            throw new IllegalStateException("이미 비활성화된 계정입니다.");
        }
        if (user.username().equals(actor)) {
            throw new IllegalStateException("현재 로그인한 계정은 비활성화할 수 없습니다.");
        }
        if ("ADMIN".equals(user.role()) && number("SELECT count(*) FROM users "
                + "WHERE role = 'ADMIN' AND active = true") <= 1) {
            throw new IllegalStateException("마지막 관리자는 비활성화할 수 없습니다.");
        }
        jdbc.update("UPDATE users SET active = false WHERE id = ?", id);
        audit.record(actor, "USER_DEACTIVATED", "USER", id, Map.of("username", user.username()));
    }

    @Transactional
    public void changePassword(String username, String currentPassword, String nextPassword) {
        if (nextPassword == null || nextPassword.length() < 16 || nextPassword.length() > 128) {
            throw new IllegalArgumentException("새 비밀번호는 16~128자로 입력하세요.");
        }
        String oldHash = jdbc.queryForObject("SELECT password_hash FROM users WHERE username = ? AND active = true",
                String.class, username);
        if (oldHash == null || !encoder.matches(currentPassword, oldHash)) {
            throw new IllegalArgumentException("현재 비밀번호가 올바르지 않습니다.");
        }
        if (encoder.matches(nextPassword, oldHash)) {
            throw new IllegalArgumentException("기존과 다른 비밀번호를 사용하세요.");
        }
        UUID id = jdbc.queryForObject("SELECT id FROM users WHERE username = ?", UUID.class, username);
        jdbc.update("UPDATE users SET password_hash = ? WHERE id = ?", encoder.encode(nextPassword), id);
        audit.record(username, "PASSWORD_CHANGED", "USER", id, Map.of());
    }

    private long number(String sql) {
        Long result = jdbc.queryForObject(sql, Long.class);
        return result == null ? 0 : result;
    }

    private UserSummary user(UUID id) {
        return jdbc.query("SELECT id, username, role, active, created_at FROM users WHERE id = ?",
                USER_MAPPER, id).stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("계정을 찾을 수 없습니다."));
    }

    public record Summary(long tasks, long signatures, long pendingEvents, long imports) { }
    public record AuditEntry(UUID id, String actor, String action, String entityType,
            UUID entityId, OffsetDateTime occurredAt) { }
    public record UserSummary(UUID id, String username, String role, boolean active,
            OffsetDateTime createdAt) { }
}

