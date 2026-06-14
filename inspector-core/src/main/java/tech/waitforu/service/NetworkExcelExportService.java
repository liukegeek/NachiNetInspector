package tech.waitforu.service;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.WorkbookUtil;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import tech.waitforu.DeviceNet;
import tech.waitforu.NachiNetResume;
import tech.waitforu.exceptions.ExcelExportException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class NetworkExcelExportService {

    private static final String NO_EXPORTABLE_DATA = "没有可导出的网络设备信息";
    private static final int MAX_SHEET_NAME_LENGTH = 31;
    private static final int MIN_COLUMN_CHARACTERS = 12;
    private static final int MAX_COLUMN_CHARACTERS = 40;
    private static final String[] HEADERS = {
            "设备名称", "IP", "子网掩码", "网关", "来源文件", "记录头",
            "记录起始偏移量", "名称长度偏移量", "名称偏移量", "IP偏移量", "掩码偏移量", "网关偏移量"
    };

    public byte[] export(List<NachiNetResume> results) {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Styles styles = createStyles(workbook);
            Set<String> usedSheetNames = new HashSet<>();

            if (results != null) {
                for (int index = 0; index < results.size(); index++) {
                    NachiNetResume result = results.get(index);
                    List<DeviceNet> devices = collectDevices(result);
                    if (devices.isEmpty()) {
                        continue;
                    }

                    String sheetName = uniqueSheetName(result.robotName(), index + 1, usedSheetNames);
                    writeSheet(workbook.createSheet(sheetName), devices, styles);
                }
            }

            if (workbook.getNumberOfSheets() == 0) {
                throw new ExcelExportException(NO_EXPORTABLE_DATA);
            }

            workbook.write(output);
            return output.toByteArray();
        } catch (IOException e) {
            throw new ExcelExportException("导出网络设备信息失败", e);
        }
    }

    private static List<DeviceNet> collectDevices(NachiNetResume result) {
        List<DeviceNet> devices = new ArrayList<>();
        if (result == null) {
            return devices;
        }
        if (result.robotSelfNet() != null) {
            devices.add(result.robotSelfNet());
        }
        if (result.subDevicesNet() != null) {
            result.subDevicesNet().stream()
                    .filter(device -> device != null)
                    .forEach(devices::add);
        }
        return devices;
    }

    private static String uniqueSheetName(String robotName, int resultIndex, Set<String> usedNames) {
        String requestedName = robotName == null || robotName.isBlank()
                ? "机器人-" + resultIndex
                : robotName;
        String safeBase = WorkbookUtil.createSafeSheetName(requestedName, '_');

        for (int duplicate = 1; ; duplicate++) {
            String suffix = duplicate == 1 ? "" : " (" + duplicate + ")";
            int baseLength = MAX_SHEET_NAME_LENGTH - suffix.length();
            String candidate = safeBase.substring(0, Math.min(safeBase.length(), baseLength)) + suffix;
            if (usedNames.add(candidate.toLowerCase(Locale.ROOT))) {
                return candidate;
            }
        }
    }

    private static void writeSheet(Sheet sheet, List<DeviceNet> devices, Styles styles) {
        writeRow(sheet.createRow(0), HEADERS, styles.header());

        for (int index = 0; index < devices.size(); index++) {
            CellStyle style = index % 2 == 0 ? styles.lightRow() : styles.alternateRow();
            writeRow(sheet.createRow(index + 1), values(devices.get(index)), style);
        }

        sheet.createFreezePane(0, 1);
        setColumnWidths(sheet);
    }

    private static void writeRow(Row row, String[] values, CellStyle style) {
        for (int index = 0; index < values.length; index++) {
            Cell cell = row.createCell(index);
            cell.setCellValue(values[index] == null ? "" : values[index]);
            cell.setCellStyle(style);
        }
    }

    private static String[] values(DeviceNet device) {
        return new String[]{
                device.deviceName(),
                device.deviceIP(),
                device.deviceMask(),
                device.deviceGateway(),
                device.sourceFile(),
                device.recordHeader(),
                device.recordStartOffset(),
                device.name_length_offset(),
                device.name_offset(),
                device.ipOffset(),
                device.maskOffset(),
                device.gatewayOffset()
        };
    }

    private static void setColumnWidths(Sheet sheet) {
        for (int column = 0; column < HEADERS.length; column++) {
            int longestValue = HEADERS[column].length();
            for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                longestValue = Math.max(
                        longestValue,
                        sheet.getRow(rowIndex).getCell(column).getStringCellValue().length());
            }
            int width = Math.max(MIN_COLUMN_CHARACTERS, longestValue + 2);
            sheet.setColumnWidth(column, Math.min(MAX_COLUMN_CHARACTERS, width) * 256);
        }
    }

    private static Styles createStyles(Workbook workbook) {
        CellStyle header = borderedStyle(workbook);
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerFont.setColor(IndexedColors.WHITE.getIndex());
        header.setFont(headerFont);
        header.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        header.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        CellStyle lightRow = borderedStyle(workbook);
        lightRow.setFillForegroundColor(IndexedColors.WHITE.getIndex());
        lightRow.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        CellStyle alternateRow = borderedStyle(workbook);
        alternateRow.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
        alternateRow.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        return new Styles(header, lightRow, alternateRow);
    }

    private static CellStyle borderedStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }

    private record Styles(CellStyle header, CellStyle lightRow, CellStyle alternateRow) {
    }
}
