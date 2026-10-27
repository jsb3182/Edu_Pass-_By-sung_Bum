package kr.go.edu.boundary.tasks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class ExcelImportServiceTest {
    @Test
    void catchesNormalizedDuplicatesBeforeAnyDatabaseWrite() throws Exception {
        byte[] workbook = workbook(new String[][] {
                {"신청번호", "신청자", "학교명", "진행상태"},
                {"AB-123", "홍길동", "가학교", "접수"},
                {"ab / 123", "김영희", "나학교", "심사"}
        });
        assertThrows(IllegalArgumentException.class, () -> ExcelImportService.parse(workbook));
    }

    @Test
    void readsRequiredColumnsInAnyOrder() throws Exception {
        byte[] workbook = workbook(new String[][] {
                {"학교명", "진행상태", "신청번호", "신청자"},
                {"가학교", "심사", "A-001", "홍길동"}
        });
        var rows = ExcelImportService.parse(workbook);
        assertEquals(1, rows.size());
        assertEquals("A001", rows.get(0).cleanNo());
        assertEquals("심사", rows.get(0).status());
    }

    @Test
    void explainsMissingHeaderRow() throws Exception {
        byte[] workbook;
        try (XSSFWorkbook book = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = book.createSheet();
            sheet.createRow(1).createCell(0).setCellValue("A-001");
            book.write(output);
            workbook = output.toByteArray();
        }
        var error = assertThrows(IllegalArgumentException.class, () -> ExcelImportService.parse(workbook));
        assertTrue(error.getMessage().contains("첫 번째 행"));
    }

    private static byte[] workbook(String[][] rows) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet();
            for (int rowIndex = 0; rowIndex < rows.length; rowIndex++) {
                var row = sheet.createRow(rowIndex);
                for (int column = 0; column < rows[rowIndex].length; column++) {
                    row.createCell(column).setCellValue(rows[rowIndex][column]);
                }
            }
            workbook.write(output);
            return output.toByteArray();
        }
    }
}
