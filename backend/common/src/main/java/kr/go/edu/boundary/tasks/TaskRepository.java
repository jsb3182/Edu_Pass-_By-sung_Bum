package kr.go.edu.boundary.tasks;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/** 신청 내역의 SQL 실행과 DB 행 변환을 담당합니다. 업무 판단은 서비스에서 합니다. */
@Repository
public class TaskRepository {
    private static final String SEARCH_CONDITION =
            "application_no_raw ILIKE ? OR applicant_name ILIKE ? OR school_name ILIKE ?";
    private static final RowMapper<TaskRecord> TASK_MAPPER = (result, row) -> map(result);
    private final JdbcTemplate jdbc;

    public TaskRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public long countMatching(String pattern) {
        Long count = jdbc.queryForObject("SELECT count(*) FROM detail_tasks WHERE " + SEARCH_CONDITION,
                Long.class, pattern, pattern, pattern);
        return count == null ? 0 : count;
    }

    public List<TaskRecord> findPage(String pattern, int size, int offset) {
        return jdbc.query("SELECT * FROM detail_tasks WHERE " + SEARCH_CONDITION
                        + " ORDER BY updated_at DESC LIMIT ? OFFSET ?",
                TASK_MAPPER, pattern, pattern, pattern, size, offset);
    }

    public Optional<TaskRecord> findById(UUID id) {
        return jdbc.query("SELECT * FROM detail_tasks WHERE id = ?", TASK_MAPPER, id).stream().findFirst();
    }

    public Optional<TaskRecord> findByCleanApplicationNo(String cleanNo) {
        return jdbc.query("SELECT * FROM detail_tasks WHERE application_no_clean = ?",
                TASK_MAPPER, cleanNo).stream().findFirst();
    }

    public void insertManual(UUID id, String applicationNo, String cleanNo,
            String applicantName, String schoolName, String status) {
        jdbc.update("INSERT INTO detail_tasks(id, application_no_raw, application_no_clean, "
                        + "applicant_name, school_name, status, source) VALUES (?, ?, ?, ?, ?, ?, 'MANUAL')",
                id, applicationNo, cleanNo, applicantName, schoolName, status);
    }

    // 갱신 행 수를 반환하여 서비스가 동시 수정 충돌을 판단하게 합니다.
    public int updateStatus(UUID id, String status, long expectedVersion) {
        return jdbc.update("UPDATE detail_tasks SET status = ?, version = version + 1, updated_at = now() "
                + "WHERE id = ? AND version = ?", status, id, expectedVersion);
    }

    public long count() {
        Long count = jdbc.queryForObject("SELECT count(*) FROM detail_tasks", Long.class);
        return count == null ? 0 : count;
    }

    private static TaskRecord map(ResultSet result) throws SQLException {
        return new TaskRecord(result.getObject("id", UUID.class), result.getString("application_no_raw"),
                result.getString("application_no_clean"), result.getString("applicant_name"),
                result.getString("school_name"), result.getString("status"), result.getString("source"),
                result.getObject("updated_at", java.time.OffsetDateTime.class), result.getLong("version"));
    }
}
