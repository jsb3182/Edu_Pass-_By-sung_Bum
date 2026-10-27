package kr.go.edu.boundary.tasks;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import kr.go.edu.boundary.common.ApplicationNumbers;
import kr.go.edu.boundary.common.AuditWriter;
import kr.go.edu.boundary.common.Digests;
import kr.go.edu.boundary.common.OutboxWriter;
import kr.go.edu.boundary.security.CurrentActor;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ExcelImportService {
    private final JdbcTemplate jdbc;
    private final TaskService tasks;
    private final CurrentActor actor;
    private final AuditWriter audit;
    private final OutboxWriter outbox;

    public ExcelImportService(JdbcTemplate jdbc, TaskService tasks, CurrentActor actor,
            AuditWriter audit, OutboxWriter outbox) {
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
        List<ImportRow> rows = parse(bytes);

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

    static List<ImportRow> parse(byte[] bytes) {
        try (Workbook workbook = WorkbookFactory.create(new java.io.ByteArrayInputStream(bytes))) {
            if (workbook.getNumberOfSheets() == 0) {
                throw new IllegalArgumentException("엑셀에 첫 번째 시트가 없습니다.");
            }
            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                throw new IllegalArgumentException("첫 번째 행에 필수 헤더가 필요합니다.");
            }
            if (sheet.getPhysicalNumberOfRows() < 2) {
                throw new IllegalArgumentException("엑셀에 데이터 행이 없습니다.");
            }
            if (sheet.getLastRowNum() > 20_000) {
                throw new IllegalArgumentException("엑셀의 행 범위가 너무 큽니다.");
            }
            DataFormatter formatter = new DataFormatter(Locale.KOREA);
            Map<String, Integer> headers = new HashMap<>();
            for (var cell : headerRow) {
                headers.put(formatter.formatCellValue(cell).strip().toLowerCase(Locale.ROOT), cell.getColumnIndex());
            }
            int noColumn = column(headers, "신청번호", "application_no");
            int nameColumn = column(headers, "신청자", "신청자명", "applicant_name");
            int schoolColumn = column(headers, "학교명", "school_name");
            int statusColumn = column(headers, "진행상태", "상태", "status");
            List<ImportRow> rows = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            for (int index = 1; index <= sheet.getLastRowNum(); index++) {
                Row row = sheet.getRow(index);
                if (row == null || row.getPhysicalNumberOfCells() == 0) {
                    continue;
                }
                String raw = value(row, noColumn, formatter);
                String name = value(row, nameColumn, formatter);
                String school = value(row, schoolColumn, formatter);
                String status = value(row, statusColumn, formatter);
                if (raw.isBlank() && name.isBlank() && school.isBlank() && status.isBlank()) {
                    continue;
                }
                if (raw.isBlank() || name.isBlank() || school.isBlank() || status.isBlank()) {
                    throw new IllegalArgumentException((index + 1) + "행의 필수값이 비어 있습니다. 전체 적재를 취소했습니다.");
                }
                if (raw.length() > 120 || name.length() > 200 || school.length() > 200
                        || status.length() > 100) {
                    throw new IllegalArgumentException((index + 1) + "행의 입력값이 너무 깁니다.");
                }
                String clean = ApplicationNumbers.clean(raw);
                if (!seen.add(clean)) {
                    throw new IllegalArgumentException((index + 1) + "행의 신청번호가 파일 안에서 중복됩니다.");
                }
                rows.add(new ImportRow(raw, clean, name, school, status));
                if (rows.size() > 5_000) {
                    throw new IllegalArgumentException("한 번에 5,000행 이하만 적재할 수 있습니다.");
                }
            }
            if (rows.isEmpty()) {
                throw new IllegalArgumentException("적재할 데이터가 없습니다.");
            }
            return rows;
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof IllegalArgumentException invalid) {
                throw invalid;
            }
            throw new IllegalArgumentException("엑셀 파일을 파싱할 수 없습니다.", exception);
        }
    }

    private static int column(Map<String, Integer> headers, String... names) {
        for (String name : names) {
            Integer index = headers.get(name);
            if (index != null) {
                return index;
            }
        }
        throw new IllegalArgumentException("필수 컬럼이 없습니다: " + String.join(" / ", names));
    }

    private static String value(Row row, int column, DataFormatter formatter) {
        var cell = row.getCell(column);
        if (cell != null && cell.getCellType() == CellType.FORMULA) {
            throw new IllegalArgumentException((row.getRowNum() + 1) + "행에 수식이 있습니다. 값으로 붙여넣어 주세요.");
        }
        return cell == null ? "" : formatter.formatCellValue(cell).strip();
    }

    record ImportRow(String rawNo, String cleanNo, String applicantName, String schoolName,
            String status) { }

    private enum UpsertResult { INSERTED, UPDATED, UNCHANGED }

    public record ImportResult(UUID batchId, int total, int inserted, int updated, int unchanged) { }
}

