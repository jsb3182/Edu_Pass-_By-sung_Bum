package kr.go.edu.boundary.tasks;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import kr.go.edu.boundary.common.ApplicationNumbers;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Component;

/** DB에 접근하지 않고 엑셀의 값, 헤더, 중복 신청번호를 검사합니다. */
@Component
public class ExcelImportParser {
    public List<ImportRow> parse(byte[] bytes) {
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

}
