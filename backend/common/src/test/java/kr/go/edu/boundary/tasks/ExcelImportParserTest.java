package kr.go.edu.boundary.tasks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class ExcelImportParserTest {
    private final ExcelImportParser parser = new ExcelImportParser();
    @Test
    void catchesNormalizedDuplicatesBeforeAnyDatabaseWrite() throws Exception {
        byte[] workbook = workbook(new String[][] {
                {"신청번호", "신청자", "학교명", "진행상태"},
                {"AB-123", "홍길동", "가학교", "접수"},
                {"ab / 123", "김영희", "나학교", "심사"}
        });
        assertThrows(IllegalArgumentException.class, () -> parser.parse(workbook));
    }

    @Test
    void readsRequiredColumnsInAnyOrder() throws Exception {
        byte[] workbook = workbook(new String[][] {
                {"학교명", "진행상태", "신청번호", "신청자"},
                {"가학교", "심사", "A-001", "홍길동"}
        });
        var rows = parser.parse(workbook);
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
        var error = assertThrows(IllegalArgumentException.class, () -> parser.parse(workbook));
        assertTrue(error.getMessage().contains("첫 번째 행"));
    }

    @Test
    void rejectsFormulaCellsInsteadOfEvaluatingThem() throws Exception {
        byte[] bytes;
        try (XSSFWorkbook book = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = book.createSheet();
            var header = sheet.createRow(0);
            String[] names = {"신청번호", "신청자", "학교명", "진행상태"};
            for (int i = 0; i < names.length; i++) header.createCell(i).setCellValue(names[i]);
            var row = sheet.createRow(1);
            row.createCell(0).setCellValue("A-001");
            row.createCell(1).setCellFormula("1+1");
            row.createCell(2).setCellValue("학교");
            row.createCell(3).setCellValue("접수");
            book.write(output);
            bytes = output.toByteArray();
        }
        var error = assertThrows(IllegalArgumentException.class, () -> parser.parse(bytes));
        assertTrue(error.getMessage().contains("수식"));
    }

    @Test
    void rejectsIncompleteRowsEvenAfterAValidRow() throws Exception {
        byte[] bytes = workbook(new String[][] {
                {"신청번호", "신청자", "학교명", "진행상태"},
                {"A-001", "방문자", "학교", "접수"},
                {"A-002", "", "학교", "접수"}
        });
        var error = assertThrows(IllegalArgumentException.class, () -> parser.parse(bytes));
        assertTrue(error.getMessage().contains("3행"));
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
