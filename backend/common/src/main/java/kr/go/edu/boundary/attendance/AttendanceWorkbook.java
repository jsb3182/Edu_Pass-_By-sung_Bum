package kr.go.edu.boundary.attendance;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.apache.poi.ss.usermodel.ClientAnchor;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

final class AttendanceWorkbook {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter SIGNED_AT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String[] HEADERS = {"번호", "교육청 소속", "직급", "성함", "전화번호", "서명 시각 (한국)", "서명"};
    private static final int[] WIDTHS = {8, 29, 20, 20, 21, 25, 28};

    private AttendanceWorkbook() { }

    static byte[] create(AttendanceService.Event event, List<AttendanceService.ExportRow> entries) {
        try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("출석부");
            var font = workbook.createFont();
            font.setBold(true);
            var heading = workbook.createCellStyle();
            heading.setFont(font);
            sheet.createRow(0).createCell(0).setCellValue(event.title());
            sheet.createRow(1).createCell(0).setCellValue("행사일 " + event.eventDate());
            var header = sheet.createRow(3);
            for (int col = 0; col < HEADERS.length; col++) {
                var cell = header.createCell(col);
                cell.setCellValue(HEADERS[col]);
                cell.setCellStyle(heading);
                sheet.setColumnWidth(col, WIDTHS[col] * 256);
            }
            sheet.createFreezePane(0, 4);
            var drawing = sheet.createDrawingPatriarch();
            for (int index = 0; index < entries.size(); index++) {
                int rowIndex = index + 4;
                var data = entries.get(index);
                var entry = data.entry();
                var row = sheet.createRow(rowIndex);
                row.setHeightInPoints(48);
                row.createCell(0).setCellValue(index + 1);
                row.createCell(1).setCellValue(entry.educationOffice());
                row.createCell(2).setCellValue(entry.jobTitle());
                row.createCell(3).setCellValue(entry.fullName());
                row.createCell(4).setCellValue(entry.phone());
                row.createCell(5).setCellValue(SIGNED_AT.format(entry.signedAt().atZoneSameInstant(SEOUL)));
                int picture = workbook.addPicture(data.image(), Workbook.PICTURE_TYPE_PNG);
                ClientAnchor anchor = workbook.getCreationHelper().createClientAnchor();
                anchor.setCol1(6);
                anchor.setCol2(7);
                anchor.setRow1(rowIndex);
                anchor.setRow2(rowIndex + 1);
                drawing.createPicture(anchor, picture);
            }
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("출석부 엑셀 파일을 만드는 데 실패했습니다.", exception);
        }
    }
}
