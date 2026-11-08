package kr.go.edu.boundary.tasks;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import kr.go.edu.boundary.tasks.ExcelImportParser.ImportRow;
import kr.go.edu.boundary.common.AuditWriter;
import kr.go.edu.boundary.common.Digests;
import kr.go.edu.boundary.common.OutboxWriter;
import kr.go.edu.boundary.security.CurrentActor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ExcelImportService {
    private final ExcelImportParser parser;
    private final JdbcTemplate jdbc;
    private final TaskService tasks;
    private final CurrentActor actor;
    private final AuditWriter audit;
    private final OutboxWriter outbox;

    public ExcelImportService(ExcelImportParser parser, JdbcTemplate jdbc, TaskService tasks, CurrentActor actor,
            AuditWriter audit, OutboxWriter outbox) {
        this.parser = parser;
        this.jdbc = jdbc;
        this.tasks = tasks;
        this.actor = actor;
        this.audit = audit;
        this.outbox = outbox;
    }

    // 한 행이라도 실패하면 변경된 내역, 감사 기록, 아웃박스가 모두 롤백됩니다.
    @Transactional
    public ImportResult importFile(MultipartFile file, Authentication authentication) {
        validateFile(file);
        byte[] bytes = readFile(file);
        List<ImportRow> rows = parser.parse(bytes);

        // 동시에 들어온 두 엑셀 적재가 같은 신청번호를 서로 덮어쓰지 않게 직렬화합니다.
        jdbc.queryForObject("SELECT pg_advisory_xact_lock(907251)", (result, row) -> result.getObject(1));
        int inserted = 0;
        int updated = 0;
        int unchanged = 0;
        for (ImportRow row : rows) {
            switch (upsert(row)) {
                case INSERTED -> inserted++;
                case UPDATED -> updated++;
                case UNCHANGED -> unchanged++;
            }
        }
        UUID batchId = saveBatch(file.getOriginalFilename(), bytes, inserted, updated, unchanged,
                authentication);
        return new ImportResult(batchId, rows.size(), inserted, updated, unchanged);
    }

    private static void validateFile(MultipartFile file) {
        if (file.isEmpty() || file.getSize() > 10_000_000
                || file.getOriginalFilename() == null
                || !file.getOriginalFilename().toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            throw new IllegalArgumentException("10MB 이하의 .xlsx 파일이 필요합니다.");
        }
    }

    private static byte[] readFile(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException exception) {
            throw new IllegalArgumentException("엑셀 파일을 읽을 수 없습니다.", exception);
        }
    }

    private UpsertResult upsert(ImportRow row) {
        var existing = tasks.findByCleanApplicationNo(row.cleanNo());
        UUID id;
        UpsertResult result;
        if (existing.isEmpty()) {
            id = UUID.randomUUID();
            jdbc.update("INSERT INTO detail_tasks(id, application_no_raw, application_no_clean, "
                            + "applicant_name, school_name, status, source) VALUES (?, ?, ?, ?, ?, ?, 'EXCEL')",
                    id, row.rawNo(), row.cleanNo(), row.applicantName(), row.schoolName(), row.status());
            result = UpsertResult.INSERTED;
        } else {
            TaskRecord old = existing.get();
            id = old.id();
            if (sameContent(old, row)) {
                return UpsertResult.UNCHANGED;
            }
            // 조회 이후 다른 관리자가 수정했다면 전체 적재를 롤백하고 다시 검토하게 합니다.
            int count = jdbc.update("UPDATE detail_tasks SET application_no_raw = ?, applicant_name = ?, "
                            + "school_name = ?, status = ?, source = 'EXCEL', updated_at = now(), "
                            + "version = version + 1 WHERE id = ? AND version = ?",
                    row.rawNo(), row.applicantName(), row.schoolName(), row.status(), id, old.version());
            if (count != 1) {
                throw new IllegalStateException("신청번호 " + row.rawNo()
                        + "가 다른 작업에서 변경되었습니다. 엑셀 내용을 확인한 뒤 다시 적재하세요.");
            }
            result = UpsertResult.UPDATED;
        }
        outbox.record("TASK_UPSERTED", id, tasks.get(id));
        return result;
    }

    private static boolean sameContent(TaskRecord old, ImportRow row) {
        return old.applicationNo().equals(row.rawNo())
                && old.applicantName().equals(row.applicantName())
                && old.schoolName().equals(row.schoolName())
                && old.status().equals(row.status());
    }

    private UUID saveBatch(String originalFilename, byte[] bytes, int inserted, int updated,
            int unchanged, Authentication authentication) {
        UUID batchId = UUID.randomUUID();
        String filename = java.nio.file.Path.of(originalFilename).getFileName().toString();
        if (filename.length() > 255) {
            throw new IllegalArgumentException("파일명이 너무 깁니다.");
        }
        jdbc.update("INSERT INTO import_batches(id, filename, sha256, inserted_count, updated_count, "
                        + "unchanged_count, imported_by) VALUES (?, ?, ?, ?, ?, ?, ?)",
                batchId, filename, Digests.sha256(bytes), inserted, updated, unchanged,
                actor.id(authentication));
        audit.record(authentication.getName(), "EXCEL_IMPORTED", "IMPORT_BATCH", batchId,
                Map.of("inserted", inserted, "updated", updated, "unchanged", unchanged));
        return batchId;
    }

    private enum UpsertResult { INSERTED, UPDATED, UNCHANGED }

    public record ImportResult(UUID batchId, int total, int inserted, int updated, int unchanged) { }
}

