package kr.go.edu.boundary.tasks;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import kr.go.edu.boundary.common.ApplicationNumbers;
import kr.go.edu.boundary.common.AuditWriter;
import kr.go.edu.boundary.common.OutboxWriter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskService {
    private static final RowMapper<TaskRecord> TASK_MAPPER = (result, row) -> map(result);
    private final JdbcTemplate jdbc;
    private final AuditWriter audit;
    private final OutboxWriter outbox;

    public TaskService(JdbcTemplate jdbc, AuditWriter audit, OutboxWriter outbox) {
        this.jdbc = jdbc;
        this.audit = audit;
        this.outbox = outbox;
    }

    public TaskPage list(String query, int page) {
        if (page < 0 || page > 10_000) {
            throw new IllegalArgumentException("페이지 번호가 올바르지 않습니다.");
        }
        int size = 50;
        String pattern = "%" + (query == null ? "" : query.strip()) + "%";
        Long total = jdbc.queryForObject("SELECT count(*) FROM detail_tasks WHERE application_no_raw ILIKE ? "
                + "OR applicant_name ILIKE ? OR school_name ILIKE ?", Long.class,
                pattern, pattern, pattern);
        List<TaskRecord> items = jdbc.query("SELECT * FROM detail_tasks WHERE application_no_raw ILIKE ? "
                        + "OR applicant_name ILIKE ? OR school_name ILIKE ? "
                        + "ORDER BY updated_at DESC LIMIT ? OFFSET ?",
                TASK_MAPPER, pattern, pattern, pattern, size, page * size);
        return new TaskPage(items, total == null ? 0 : total, page, size);
    }

    public TaskRecord get(UUID id) {
        return jdbc.query("SELECT * FROM detail_tasks WHERE id = ?", TASK_MAPPER, id).stream()
                .findFirst().orElseThrow(() -> new IllegalArgumentException("내역을 찾을 수 없습니다."));
    }

    // 엑셀과 직접 등록은 같은 정제 신청번호를 기준으로 동일한 내역을 찾습니다.
    public Optional<TaskRecord> findByCleanApplicationNo(String cleanNo) {
        return jdbc.query("SELECT * FROM detail_tasks WHERE application_no_clean = ?",
                TASK_MAPPER, cleanNo).stream().findFirst();
    }

    @Transactional
    public TaskRecord create(String applicationNo, String applicantName, String schoolName,
            String status, String actor) {
        String clean = ApplicationNumbers.clean(applicationNo);
        requireText(applicationNo, "신청번호", 120);
        requireText(applicantName, "신청자", 200);
        requireText(schoolName, "학교", 200);
        requireText(status, "상태", 100);
        if (findByCleanApplicationNo(clean).isPresent()) {
            throw new IllegalStateException("동일한 신청번호가 이미 있습니다.");
        }
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO detail_tasks(id, application_no_raw, application_no_clean, "
                        + "applicant_name, school_name, status, source) VALUES (?, ?, ?, ?, ?, ?, 'MANUAL')",
                id, applicationNo.strip(), clean, applicantName.strip(), schoolName.strip(), status.strip());
        TaskRecord task = get(id);
        audit.record(actor, "TASK_CREATED", "TASK", id, Map.of("applicationNo", clean));
        outbox.record("TASK_UPSERTED", id, task);
        return task;
    }

    @Transactional
    public TaskRecord changeStatus(UUID id, String status, long expectedVersion, String actor) {
        requireText(status, "상태", 100);
        // version 조건으로 다른 관리자의 변경을 조용히 덮어쓰지 않게 합니다.
        int updated = jdbc.update("UPDATE detail_tasks SET status = ?, version = version + 1, updated_at = now() "
                        + "WHERE id = ? AND version = ?", status.strip(), id, expectedVersion);
        if (updated != 1) {
            throw new IllegalStateException("다른 사용자가 내역을 변경했습니다. 새로고침 후 다시 시도하세요.");
        }
        TaskRecord task = get(id);
        audit.record(actor, "TASK_STATUS_CHANGED", "TASK", id, Map.of("status", task.status()));
        outbox.record("TASK_UPSERTED", id, task);
        return task;
    }

    public long count() {
        Long count = jdbc.queryForObject("SELECT count(*) FROM detail_tasks", Long.class);
        return count == null ? 0 : count;
    }

    private static void requireText(String value, String label, int maxLength) {
        if (value == null || value.isBlank() || value.length() > maxLength) {
            throw new IllegalArgumentException(label + " 입력값이 올바르지 않습니다.");
        }
    }

    private static TaskRecord map(ResultSet result) throws SQLException {
        return new TaskRecord(result.getObject("id", UUID.class), result.getString("application_no_raw"),
                result.getString("application_no_clean"), result.getString("applicant_name"),
                result.getString("school_name"), result.getString("status"), result.getString("source"),
                result.getObject("updated_at", java.time.OffsetDateTime.class), result.getLong("version"));
    }

    public record TaskPage(List<TaskRecord> items, long total, int page, int size) { }
}

