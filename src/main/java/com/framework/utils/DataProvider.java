package com.framework.utils;

import com.opencsv.CSVReader;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.*;
import java.util.*;

/**
 * Utility for loading test data from various sources.
 * - CSV files
 * - Excel (XLSX) files
 * - JSON files
 * - In-memory builders
 */
@Slf4j
public class DataProvider {

    private DataProvider() {}

    // ------------------------------------------------------------------ //
    //  CSV                                                                 //
    // ------------------------------------------------------------------ //

    /**
     * Load a CSV file from the classpath. Returns a list of row maps
     * (first row is assumed to be headers).
     */
    public static List<Map<String, String>> loadCsv(String classpathPath) {
        List<Map<String, String>> data = new ArrayList<>();
        try (InputStream is = DataProvider.class.getClassLoader().getResourceAsStream(classpathPath);
             CSVReader reader = new CSVReader(new InputStreamReader(Objects.requireNonNull(is)))) {
            String[] headers = reader.readNext();
            if (headers == null) return data;
            String[] row;
            while ((row = reader.readNext()) != null) {
                Map<String, String> map = new LinkedHashMap<>();
                for (int i = 0; i < headers.length && i < row.length; i++) {
                    map.put(headers[i].trim(), row[i].trim());
                }
                data.add(map);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load CSV: " + classpathPath, e);
        }
        log.info("Loaded {} rows from CSV: {}", data.size(), classpathPath);
        return data;
    }

    /**
     * Convert CSV data to a TestNG 2D Object array for @DataProvider methods.
     */
    public static Object[][] csvToTestNgData(String classpathPath) {
        List<Map<String, String>> rows = loadCsv(classpathPath);
        return rows.stream().map(r -> new Object[]{r}).toArray(Object[][]::new);
    }

    // ------------------------------------------------------------------ //
    //  Excel                                                               //
    // ------------------------------------------------------------------ //

    /**
     * Load a named sheet from an Excel (.xlsx) file on the classpath.
     */
    public static List<Map<String, String>> loadExcel(String classpathPath, String sheetName) {
        List<Map<String, String>> data = new ArrayList<>();
        try (InputStream is = DataProvider.class.getClassLoader().getResourceAsStream(classpathPath);
             Workbook wb = new XSSFWorkbook(Objects.requireNonNull(is))) {
            Sheet sheet = wb.getSheet(sheetName);
            if (sheet == null) throw new IllegalArgumentException("Sheet not found: " + sheetName);
            Row headerRow = sheet.getRow(0);
            List<String> headers = new ArrayList<>();
            headerRow.forEach(cell -> headers.add(cell.getStringCellValue().trim()));
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;
                Map<String, String> map = new LinkedHashMap<>();
                for (int c = 0; c < headers.size(); c++) {
                    Cell cell = row.getCell(c, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                    map.put(headers.get(c), cell != null ? getCellValue(cell) : "");
                }
                data.add(map);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load Excel: " + classpathPath, e);
        }
        log.info("Loaded {} rows from Excel: {} [{}]", data.size(), classpathPath, sheetName);
        return data;
    }

    private static String getCellValue(Cell cell) {
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> DateUtil.isCellDateFormatted(cell)
                    ? cell.getDateCellValue().toString()
                    : String.valueOf((long) cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> cell.getCellFormula();
            default -> "";
        };
    }

    // ------------------------------------------------------------------ //
    //  Builder                                                             //
    // ------------------------------------------------------------------ //

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private final List<Object[]> rows = new ArrayList<>();
        public Builder row(Object... values) { rows.add(values); return this; }
        public Object[][] build() { return rows.toArray(new Object[0][]); }
    }
}
