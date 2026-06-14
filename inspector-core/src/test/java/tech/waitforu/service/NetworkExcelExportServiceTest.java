package tech.waitforu.service;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import tech.waitforu.DeviceNet;
import tech.waitforu.NachiNetResume;
import tech.waitforu.exceptions.ExcelExportException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetworkExcelExportServiceTest {

    private static final String[] HEADERS = {
            "设备名称", "IP", "子网掩码", "网关", "来源文件", "记录头",
            "记录起始偏移量", "名称长度偏移量", "名称偏移量", "IP偏移量", "掩码偏移量", "网关偏移量"
    };

    private final NetworkExcelExportService service = new NetworkExcelExportService();

    @Test
    void exportsBodyThenChildrenWithFixedHeadersAndFormatting() throws IOException {
        DeviceNet body = device("body", "10.0.0.1");
        DeviceNet child = device("child", null);
        NachiNetResume result = result("R1", true, body, List.of(child));

        try (Workbook workbook = workbook(service.export(List.of(result)))) {
            assertEquals(1, workbook.getNumberOfSheets());
            Sheet sheet = workbook.getSheetAt(0);
            assertEquals("R1", sheet.getSheetName());
            assertRowValues(sheet.getRow(0), HEADERS);
            assertRowValues(sheet.getRow(1), values(body));
            assertRowValues(sheet.getRow(2), values(child));
            assertEquals("", sheet.getRow(2).getCell(1).getStringCellValue());

            assertNotNull(sheet.getPaneInformation());
            assertTrue(sheet.getPaneInformation().isFreezePane());
            assertEquals(1, sheet.getPaneInformation().getHorizontalSplitPosition());

            Cell header = sheet.getRow(0).getCell(0);
            Font headerFont = workbook.getFontAt(header.getCellStyle().getFontIndex());
            assertTrue(headerFont.getBold());
            assertEquals(IndexedColors.WHITE.getIndex(), headerFont.getColor());
            assertEquals(IndexedColors.DARK_BLUE.getIndex(), header.getCellStyle().getFillForegroundColor());
            assertEquals(FillPatternType.SOLID_FOREGROUND, header.getCellStyle().getFillPattern());
            assertEquals(BorderStyle.THIN, header.getCellStyle().getBorderBottom());
            assertEquals(BorderStyle.THIN, sheet.getRow(1).getCell(0).getCellStyle().getBorderBottom());
            assertNotEquals(
                    sheet.getRow(1).getCell(0).getCellStyle().getFillForegroundColor(),
                    sheet.getRow(2).getCell(0).getCellStyle().getFillForegroundColor());
            assertTrue(sheet.getColumnWidth(0) > 0);
            assertTrue(sheet.getColumnWidth(0) <= 50 * 256);
        }
    }

    @Test
    void rejectsEmptyResults() {
        ExcelExportException exception = assertThrows(
                ExcelExportException.class,
                () -> service.export(List.of()));

        assertEquals("没有可导出的网络设备信息", exception.getMessage());
    }

    @Test
    void rejectsResultsWithoutDevices() {
        ExcelExportException exception = assertThrows(
                ExcelExportException.class,
                () -> service.export(List.of(result("R1", true, null, List.of()))));

        assertEquals("没有可导出的网络设备信息", exception.getMessage());
    }

    @Test
    void makesDuplicateInvalidRobotNamesSafeUniqueAndWithinExcelLimits() throws IOException {
        List<NachiNetResume> results = List.of(
                result("R/1", true, device("body-1", "10.0.0.1"), List.of()),
                result("R/1", true, device("body-2", "10.0.0.2"), List.of()));

        try (Workbook workbook = workbook(service.export(results))) {
            String firstName = workbook.getSheetName(0);
            String secondName = workbook.getSheetName(1);

            assertFalse(firstName.matches(".*[\\\\/?*\\[\\]:].*"));
            assertFalse(secondName.matches(".*[\\\\/?*\\[\\]:].*"));
            assertNotEquals(firstName, secondName);
            assertTrue(firstName.length() <= 31);
            assertTrue(secondName.length() <= 31);
        }
    }

    @Test
    void exportsPartialResultWithUsableDeviceAndSkipsUnusableResult() throws IOException {
        NachiNetResume unusable = result("empty", false, null, null);
        DeviceNet child = device("partial-child", "10.0.0.3");
        NachiNetResume partial = result(null, false, null, List.of(child));

        try (Workbook workbook = workbook(service.export(List.of(unusable, partial)))) {
            assertEquals(1, workbook.getNumberOfSheets());
            assertEquals("机器人-2", workbook.getSheetName(0));
            assertRowValues(workbook.getSheetAt(0).getRow(1), values(child));
        }
    }

    @Test
    void excelExportExceptionSupportsMessageAndCause() {
        IOException cause = new IOException("disk full");

        ExcelExportException messageOnly = new ExcelExportException("failed");
        ExcelExportException withCause = new ExcelExportException("failed", cause);

        assertEquals("failed", messageOnly.getMessage());
        assertEquals("failed", withCause.getMessage());
        assertEquals(cause, withCause.getCause());
    }

    private static Workbook workbook(byte[] bytes) throws IOException {
        return new XSSFWorkbook(new ByteArrayInputStream(bytes));
    }

    private static NachiNetResume result(
            String robotName,
            boolean success,
            DeviceNet body,
            List<DeviceNet> children
    ) {
        return new NachiNetResume(robotName, success, body, children, List.of());
    }

    private static DeviceNet device(String name, String ip) {
        return new DeviceNet(
                name,
                ip,
                "255.255.255.0",
                "10.0.0.254",
                "config.nxd",
                "ABCD",
                "0x01",
                "0x02",
                "0x03",
                "0x04",
                "0x05",
                "0x06");
    }

    private static String[] values(DeviceNet device) {
        return new String[]{
                blankIfNull(device.deviceName()),
                blankIfNull(device.deviceIP()),
                blankIfNull(device.deviceMask()),
                blankIfNull(device.deviceGateway()),
                blankIfNull(device.sourceFile()),
                blankIfNull(device.recordHeader()),
                blankIfNull(device.recordStartOffset()),
                blankIfNull(device.name_length_offset()),
                blankIfNull(device.name_offset()),
                blankIfNull(device.ipOffset()),
                blankIfNull(device.maskOffset()),
                blankIfNull(device.gatewayOffset())
        };
    }

    private static String blankIfNull(String value) {
        return value == null ? "" : value;
    }

    private static void assertRowValues(Row row, String[] expected) {
        assertNotNull(row);
        assertEquals(expected.length, row.getLastCellNum());
        assertArrayEquals(
                expected,
                java.util.stream.IntStream.range(0, expected.length)
                        .mapToObj(index -> row.getCell(index).getStringCellValue())
                        .toArray(String[]::new));
    }
}
