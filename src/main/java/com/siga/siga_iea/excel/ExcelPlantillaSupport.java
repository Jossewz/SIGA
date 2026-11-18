package com.siga.siga_iea.excel;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.*;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * Utilidad compartida para generación, firmado técnico y validación estricta
 * de plantillas oficiales Excel (.xlsx) en SIGA IEACI.
 */
public final class ExcelPlantillaSupport {

    public static final String META_SHEET_NAME = "__SIGA_META__";
    public static final String TIPO_ASISTENCIA = "SIGA_ASISTENCIA_V1";
    public static final String TIPO_CALIFICACIONES = "SIGA_CALIFICACIONES_V1";
    public static final int HEADER_ROW_INDEX = 4; // Fila 5 en Excel (1-based)
    public static final int DATA_START_ROW_INDEX = 5; // Fila 6 en Excel (1-based)

    private static final long MAX_EXCEL_BYTES = 5L * 1024 * 1024;
    private static final String SIGNATURE_SALT = "SIGA_IEACI_EXCEL_V1_SALT";
    private static final MediaType XLSX_MEDIA_TYPE = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private ExcelPlantillaSupport() {
    }

    public record ValidatedWorkbook(XSSFWorkbook workbook, Sheet dataSheet, Map<String, String> meta) {
    }

    public static void writeMetaSheet(XSSFWorkbook workbook, Map<String, String> rawMeta) {
        Map<String, String> meta = new LinkedHashMap<>(rawMeta);
        meta.put("firma", computeSignature(meta));

        Sheet metaSheet = workbook.createSheet(META_SHEET_NAME);
        int rowIdx = 0;
        for (Map.Entry<String, String> entry : meta.entrySet()) {
            Row r = metaSheet.createRow(rowIdx++);
            r.createCell(0).setCellValue(entry.getKey());
            r.createCell(1).setCellValue(entry.getValue() != null ? entry.getValue() : "");
        }
        int idx = workbook.getSheetIndex(META_SHEET_NAME);
        workbook.setSheetVisibility(idx, SheetVisibility.VERY_HIDDEN);
    }

    public static ValidatedWorkbook openAndValidate(MultipartFile file, String expectedTemplateType) {
        if (file == null || file.isEmpty()) {
            throw new ExcelValidationException("Debe seleccionar un archivo Excel (.xlsx) para importar.");
        }
        String originalName = file.getOriginalFilename();
        if (originalName == null || !originalName.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            throw new ExcelValidationException("Formato inválido. Solo se permiten archivos Excel (.xlsx) generados desde SIGA.");
        }
        if (file.getSize() > MAX_EXCEL_BYTES) {
            throw new ExcelValidationException("El archivo supera el tamaño máximo permitido de 5 MB.");
        }

        XSSFWorkbook workbook;
        try {
            workbook = new XSSFWorkbook(file.getInputStream());
        } catch (Exception e) {
            throw new ExcelValidationException("No fue posible leer el archivo Excel (.xlsx). Verifique que no esté dañado o protegido con contraseña.");
        }

        Sheet metaSheet = workbook.getSheet(META_SHEET_NAME);
        if (metaSheet == null) {
            closeQuietly(workbook);
            throw new ExcelValidationException("El archivo no corresponde a una plantilla oficial descargada desde SIGA.");
        }

        Map<String, String> meta = new LinkedHashMap<>();
        for (int i = 0; i <= metaSheet.getLastRowNum(); i++) {
            Row r = metaSheet.getRow(i);
            if (r == null) continue;
            String k = readCellAsString(r.getCell(0));
            String v = readCellAsString(r.getCell(1));
            if (k != null && !k.isBlank()) {
                meta.put(k.trim(), v != null ? v.trim() : "");
            }
        }

        String firmaActual = meta.getOrDefault("firma", "");
        String firmaEsperada = computeSignature(meta);
        if (!firmaEsperada.equals(firmaActual)) {
            closeQuietly(workbook);
            throw new ExcelValidationException("La plantilla Excel fue alterada o su firma de integridad no es válida. Descargue una plantilla nueva desde SIGA.");
        }

        String templateType = meta.getOrDefault("templateType", "");
        if (!expectedTemplateType.equals(templateType)) {
            closeQuietly(workbook);
            throw new ExcelValidationException("La plantilla cargada corresponde a otro módulo (" + templateType + ") y no puede importarse aquí.");
        }

        Sheet dataSheet = workbook.getNumberOfSheets() > 0 ? workbook.getSheetAt(0) : null;
        if (dataSheet == null || META_SHEET_NAME.equals(dataSheet.getSheetName())) {
            closeQuietly(workbook);
            throw new ExcelValidationException("La plantilla no contiene una hoja principal de datos válida.");
        }

        return new ValidatedWorkbook(workbook, dataSheet, meta);
    }

    public static void validateHeaderRow(Sheet sheet, int headerRowIndex, List<String> expectedHeaders) {
        Row headerRow = sheet.getRow(headerRowIndex);
        if (headerRow == null) {
            throw new ExcelValidationException(
                    "No se encontró la fila de encabezados oficial en la fila " + (headerRowIndex + 1) + ".");
        }
        List<String> errores = new ArrayList<>();
        for (int col = 0; col < expectedHeaders.size(); col++) {
            String expected = expectedHeaders.get(col);
            String actual = readCellAsString(headerRow.getCell(col));
            if (actual == null || !expected.equalsIgnoreCase(actual.trim())) {
                errores.add("Columna " + (col + 1) + ": se esperaba encabezado '" + expected + "' pero se encontró '" + (actual != null ? actual : "vacío") + "'.");
            }
        }
        if (!errores.isEmpty()) {
            throw new ExcelValidationException(
                    "Los encabezados de la plantilla no coinciden con la estructura oficial.",
                    errores);
        }
    }

    public static void renderTitleBanner(XSSFSheet sheet, XSSFWorkbook wb, int totalCols, String title, String subtitle, String instructions) {
        XSSFCellStyle titleStyle = createBannerStyle(wb);
        XSSFCellStyle subStyle = createSubtitleStyle(wb);
        XSSFCellStyle instrStyle = createInstructionStyle(wb);

        Row r0 = sheet.createRow(0);
        r0.setHeightInPoints(26);
        Cell c0 = r0.createCell(0);
        c0.setCellValue(title);
        c0.setCellStyle(titleStyle);

        Row r1 = sheet.createRow(1);
        r1.setHeightInPoints(20);
        Cell c1 = r1.createCell(0);
        c1.setCellValue(subtitle);
        c1.setCellStyle(subStyle);

        Row r2 = sheet.createRow(2);
        r2.setHeightInPoints(19);
        Cell c2 = r2.createCell(0);
        c2.setCellValue(instructions);
        c2.setCellStyle(instrStyle);

        if (totalCols > 1) {
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, totalCols - 1));
            sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, totalCols - 1));
            sheet.addMergedRegion(new CellRangeAddress(2, 2, 0, totalCols - 1));
        }
        sheet.createRow(3).setHeightInPoints(8);
    }

    public static XSSFCellStyle createHeaderStyle(XSSFWorkbook wb) {
        XSSFCellStyle style = wb.createCellStyle();
        style.setFillForegroundColor(new XSSFColor(new byte[]{(byte) 0xEB, (byte) 0xF3, (byte) 0xE8}, null));
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        XSSFFont font = wb.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 10);
        font.setColor(new XSSFColor(new byte[]{(byte) 0x0D, (byte) 0x41, (byte) 0x17}, null));
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        applyThinBorders(style);
        return style;
    }

    public static XSSFCellStyle createReadonlyCellStyle(XSSFWorkbook wb, HorizontalAlignment align) {
        XSSFCellStyle style = wb.createCellStyle();
        style.setFillForegroundColor(new XSSFColor(new byte[]{(byte) 0xF7, (byte) 0xF9, (byte) 0xF7}, null));
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        XSSFFont font = wb.createFont();
        font.setFontHeightInPoints((short) 10);
        font.setColor(new XSSFColor(new byte[]{(byte) 0x24, (byte) 0x3D, (byte) 0x28}, null));
        style.setFont(font);
        style.setAlignment(align);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        applyThinBorders(style);
        return style;
    }

    public static XSSFCellStyle createEditableCellStyle(XSSFWorkbook wb, HorizontalAlignment align, boolean decimalFormat) {
        XSSFCellStyle style = wb.createCellStyle();
        style.setFillForegroundColor(new XSSFColor(new byte[]{(byte) 0xFF, (byte) 0xFF, (byte) 0xFF}, null));
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        XSSFFont font = wb.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 10);
        font.setColor(new XSSFColor(new byte[]{(byte) 0x0D, (byte) 0x41, (byte) 0x17}, null));
        style.setFont(font);
        style.setAlignment(align);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        if (decimalFormat) {
            style.setDataFormat(wb.createDataFormat().getFormat("0.00"));
        }
        applyThinBorders(style);
        return style;
    }

    public static void addDropdownValidation(XSSFSheet sheet, int firstRow, int lastRow, int col, String[] options, String errorTitle, String errorMessage) {
        if (lastRow < firstRow) return;
        XSSFDataValidationHelper helper = new XSSFDataValidationHelper(sheet);
        DataValidationConstraint constraint = helper.createExplicitListConstraint(options);
        CellRangeAddressList range = new CellRangeAddressList(firstRow, lastRow, col, col);
        DataValidation validation = helper.createValidation(constraint, range);
        validation.setShowErrorBox(true);
        validation.setErrorStyle(DataValidation.ErrorStyle.STOP);
        validation.createErrorBox(errorTitle, errorMessage);
        validation.setSuppressDropDownArrow(true);
        sheet.addValidationData(validation);
    }

    public static void addDecimalRangeValidation(XSSFSheet sheet, int firstRow, int lastRow, int firstCol, int lastCol, String min, String max) {
        if (lastRow < firstRow || lastCol < firstCol) return;
        XSSFDataValidationHelper helper = new XSSFDataValidationHelper(sheet);
        DataValidationConstraint constraint = helper.createDecimalConstraint(
                DataValidationConstraint.OperatorType.BETWEEN, min, max);
        CellRangeAddressList range = new CellRangeAddressList(firstRow, lastRow, firstCol, lastCol);
        DataValidation validation = helper.createValidation(constraint, range);
        validation.setShowErrorBox(true);
        validation.setErrorStyle(DataValidation.ErrorStyle.STOP);
        validation.createErrorBox("Nota fuera de rango", "Ingrese una calificación numérica entre " + min + " y " + max + ".");
        sheet.addValidationData(validation);
    }

    public static String readCellAsString(Cell cell) {
        if (cell == null) return "";
        CellType type = cell.getCellType();
        if (type == CellType.FORMULA) {
            type = cell.getCachedFormulaResultType();
        }
        switch (type) {
            case STRING -> {
                return cell.getStringCellValue() != null ? cell.getStringCellValue().trim() : "";
            }
            case NUMERIC -> {
                BigDecimal bd = BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros();
                return bd.scale() < 0 ? bd.setScale(0, RoundingMode.UNNECESSARY).toPlainString() : bd.toPlainString();
            }
            case BOOLEAN -> {
                return Boolean.toString(cell.getBooleanCellValue());
            }
            case BLANK, _NONE, ERROR -> {
                return "";
            }
        }
        return "";
    }

    public static boolean isRowEmpty(Row row, int maxCols) {
        if (row == null) return true;
        for (int i = 0; i < maxCols; i++) {
            String val = readCellAsString(row.getCell(i));
            if (val != null && !val.isBlank()) {
                return false;
            }
        }
        return true;
    }

    public static Optional<BigDecimal> parseNotaOptional(Cell cell) {
        if (cell == null || cell.getCellType() == CellType.BLANK) {
            return Optional.empty();
        }
        CellType type = cell.getCellType();
        if (type == CellType.FORMULA) {
            type = cell.getCachedFormulaResultType();
        }
        if (type == CellType.NUMERIC) {
            BigDecimal val = BigDecimal.valueOf(cell.getNumericCellValue()).setScale(2, RoundingMode.HALF_UP);
            return Optional.of(val);
        }
        String raw = readCellAsString(cell);
        if (raw.isBlank()) {
            return Optional.empty();
        }
        String normalized = raw.replace(',', '.');
        try {
            BigDecimal val = new BigDecimal(normalized).setScale(2, RoundingMode.HALF_UP);
            return Optional.of(val);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Valor no numérico '" + raw + "'");
        }
    }

    public static byte[] toBytes(XSSFWorkbook workbook) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Error al generar el archivo Excel (.xlsx).", e);
        }
    }

    public static ResponseEntity<byte[]> buildDownloadResponse(byte[] content, String filename) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(XLSX_MEDIA_TYPE);
        headers.setContentDisposition(ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build());
        return ResponseEntity.ok().headers(headers).body(content);
    }

    public static String sanitizeFilenamePart(String raw) {
        if (raw == null || raw.isBlank()) return "General";
        return raw.trim()
                .replaceAll("[^A-Za-z0-9._-]+", "_")
                .replaceAll("^_+|_+$", "");
    }

    public static void closeQuietly(XSSFWorkbook wb) {
        if (wb != null) {
            try {
                wb.close();
            } catch (IOException ignored) {
            }
        }
    }

    private static XSSFCellStyle createBannerStyle(XSSFWorkbook wb) {
        XSSFCellStyle style = wb.createCellStyle();
        style.setFillForegroundColor(new XSSFColor(new byte[]{(byte) 0x0D, (byte) 0x41, (byte) 0x17}, null));
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        XSSFFont font = wb.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 12);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.LEFT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setIndention((short) 1);
        return style;
    }

    private static XSSFCellStyle createSubtitleStyle(XSSFWorkbook wb) {
        XSSFCellStyle style = wb.createCellStyle();
        style.setFillForegroundColor(new XSSFColor(new byte[]{(byte) 0xF4, (byte) 0xFB, (byte) 0xF2}, null));
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        XSSFFont font = wb.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 10);
        font.setColor(new XSSFColor(new byte[]{(byte) 0x1A, (byte) 0x3A, (byte) 0x1F}, null));
        style.setFont(font);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setIndention((short) 1);
        return style;
    }

    private static XSSFCellStyle createInstructionStyle(XSSFWorkbook wb) {
        XSSFCellStyle style = wb.createCellStyle();
        style.setFillForegroundColor(new XSSFColor(new byte[]{(byte) 0xFF, (byte) 0xFB, (byte) 0xEB}, null));
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        XSSFFont font = wb.createFont();
        font.setItalic(true);
        font.setFontHeightInPoints((short) 9);
        font.setColor(new XSSFColor(new byte[]{(byte) 0x78, (byte) 0x35, (byte) 0x0F}, null));
        style.setFont(font);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setIndention((short) 1);
        return style;
    }

    private static void applyThinBorders(CellStyle style) {
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setTopBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setBottomBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setLeftBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setRightBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
    }

    private static String computeSignature(Map<String, String> meta) {
        String payload = String.join("|",
                meta.getOrDefault("templateType", ""),
                meta.getOrDefault("scope", ""),
                meta.getOrDefault("cursoId", ""),
                meta.getOrDefault("sesionId", ""),
                meta.getOrDefault("cursoMateriaId", ""),
                meta.getOrDefault("materiaId", ""),
                meta.getOrDefault("fecha", ""),
                meta.getOrDefault("periodo", ""),
                meta.getOrDefault("evaluacionIds", ""),
                SIGNATURE_SALT
        );
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }
}

