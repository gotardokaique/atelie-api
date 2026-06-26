package com.gestao.api.services;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

public class WorkService {

    private final Workbook workbook;

    private Sheet sheet;
    private int currentRow;
    private int colCount;

    private String fileName = "relatorio.xlsx";

    // estilos
    private CellStyle headerStyle;
    private CellStyle firstColumnStyle;
    private CellStyle titleStyle;

    // cores
    private final Map<String, Short> colorMap = new HashMap<>();

    // controle de título (para mesclar depois que souber colCount)
    private Integer pendingTitleRowIndex = null;

    // flags de formatação
    private boolean titleUpperCase = false;
    private boolean colUpperCase = false;

    // formatação BR para datas
    private static final DateTimeFormatter DATE_BR =
            DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public WorkService() {
        this.workbook = new XSSFWorkbook();
        initColorMap();
        initDefaultStyles();
        // IMPORTANTE: não cria sheet aqui (evita aba vazia)
    }

    /* ======================================================
       ABA / TÍTULO
       ====================================================== */

    public WorkService setAba(String nome) {
        if (nome == null || nome.trim().isEmpty()) nome = "Sheet1";

        Sheet existing = workbook.getSheet(nome);
        this.sheet = (existing != null) ? existing : workbook.createSheet(nome);

        this.currentRow = 0;
        this.colCount = 0;
        this.pendingTitleRowIndex = null;

        // reset por aba (pra não vazar config)
        this.titleUpperCase = false;
        this.colUpperCase = false;

        return this;
    }

    public WorkService titleUpperCase() {
        this.titleUpperCase = true;
        return this;
    }

    public WorkService colUpperCase() {
        this.colUpperCase = true;
        return this;
    }

    public WorkService setTitle(String title) {
        ensureSheet();

        Row row = sheet.createRow(currentRow++);
        Cell cell = row.createCell(0);

        String value = title == null ? "" : title;
        if (titleUpperCase) {
            value = value.toUpperCase();
        }

        cell.setCellValue(value);
        cell.setCellStyle(titleStyle);

        // ainda não sabemos quantas colunas existem se createCol não foi chamado
        pendingTitleRowIndex = row.getRowNum();

        // se já existir colCount, mescla agora
        tryMergeTitleIfPossible();

        return this;
    }

    /* ======================================================
       COLUNAS / LINHAS
       ====================================================== */

    public WorkService createCol(String... columns) {
        ensureSheet();

        if (columns == null || columns.length == 0) {
            throw new IllegalArgumentException("createCol requer ao menos 1 coluna");
        }

        Row header = sheet.createRow(currentRow++);
        colCount = columns.length;

        for (int i = 0; i < columns.length; i++) {
            Cell cell = header.createCell(i);

            String value = columns[i] == null ? "" : columns[i];
            if (colUpperCase) {
                value = value.toUpperCase();
            }

            cell.setCellValue(value);
            cell.setCellStyle(headerStyle);
        }

        // congela até o header
        sheet.createFreezePane(0, currentRow);

        // filtro no header
        sheet.setAutoFilter(new CellRangeAddress(
                header.getRowNum(),
                header.getRowNum(),
                0,
                colCount - 1
        ));

        // agora que sabemos colCount, mescla o título se ele já foi escrito
        tryMergeTitleIfPossible();

        return this;
    }

    public WorkService createRow(Object... values) {
        ensureSheet();

        if (colCount <= 0) {
            throw new IllegalStateException("Chame createCol(...) antes de createRow(...)");
        }

        Row row = sheet.createRow(currentRow++);

        for (int i = 0; i < colCount; i++) {
            Cell cell = row.createCell(i);
            Object val = (values != null && i < values.length) ? values[i] : null;

            cell.setCellValue(formatValue(val));

            if (i == 0 && firstColumnStyle != null) {
                cell.setCellStyle(firstColumnStyle);
            }
        }
        return this;
    }

    /* ======================================================
       ESTILOS (TAILWIND-LIKE)
       ====================================================== */

    public WorkService setColorCabecalho(String bgClass) {
        Short color = resolveColor(bgClass);
        if (color != null) {
            headerStyle.setFillForegroundColor(color);
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        }
        return this;
    }

    public WorkService setColorPrimeiraColuna(String bgClass) {
        Short color = resolveColor(bgClass);
        if (color != null) {
            firstColumnStyle = workbook.createCellStyle();
            firstColumnStyle.setFillForegroundColor(color);
            firstColumnStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        }
        return this;
    }

    /* ======================================================
       FILE / BYTES
       ====================================================== */

    public WorkService fileName(String fileName) {
        if (fileName != null && !fileName.trim().isEmpty()) {
            this.fileName = fileName.trim();
        }
        return this;
    }

    public byte[] getBytesXLSX() {
        ensureSheet(); // garante que ao menos uma aba existe
        autoSizeAllSheets();

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            workbook.write(out);
            workbook.close();
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Erro ao gerar XLSX", e);
        }
    }

    public File getFileXLSX() {
        ensureSheet();
        autoSizeAllSheets();

        try {
            File file = File.createTempFile(
                    fileName.replace(".xlsx", ""),
                    ".xlsx"
            );

            try (FileOutputStream out = new FileOutputStream(file)) {
                workbook.write(out);
            }

            workbook.close();
            return file;

        } catch (IOException e) {
            throw new RuntimeException("Erro ao gerar XLSX", e);
        }
    }

    /* ======================================================
       INTERNALS
       ====================================================== */

    private void ensureSheet() {
        if (this.sheet == null) {
            this.sheet = workbook.createSheet("Sheet1");
            this.currentRow = 0;
            this.colCount = 0;
            this.pendingTitleRowIndex = null;
        }
    }

    private String formatValue(Object val) {
        if (val == null) return "";

        if (val instanceof LocalDate ld) {
            return ld.format(DATE_BR);
        }

        if (val instanceof LocalDateTime ldt) {
            return ldt.toLocalDate().format(DATE_BR);
        }

        return val.toString();
    }

    private void initDefaultStyles() {
        headerStyle = workbook.createCellStyle();
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerStyle.setFont(headerFont);

        titleStyle = workbook.createCellStyle();
        Font titleFont = workbook.createFont();
        titleFont.setBold(true);
        titleFont.setFontHeightInPoints((short) 14);
        titleStyle.setFont(titleFont);

        firstColumnStyle = null;
    }

    private void tryMergeTitleIfPossible() {
        if (pendingTitleRowIndex == null) return;
        if (colCount <= 1) return;

        // evita repetir merge
        for (int i = 0; i < sheet.getNumMergedRegions(); i++) {
            CellRangeAddress r = sheet.getMergedRegion(i);
            if (r.getFirstRow() == pendingTitleRowIndex && r.getLastRow() == pendingTitleRowIndex) {
                return;
            }
        }

        sheet.addMergedRegion(new CellRangeAddress(
                pendingTitleRowIndex,
                pendingTitleRowIndex,
                0,
                colCount - 1
        ));
    }

    private void autoSizeAllSheets() {
        for (int s = 0; s < workbook.getNumberOfSheets(); s++) {
            Sheet sh = workbook.getSheetAt(s);

            int maxCol = 0;
            for (int r = 0; r <= sh.getLastRowNum(); r++) {
                Row row = sh.getRow(r);
                if (row != null && row.getLastCellNum() > maxCol) {
                    maxCol = row.getLastCellNum();
                }
            }

            for (int c = 0; c < maxCol; c++) {
                sh.autoSizeColumn(c);
                sh.setColumnWidth(c, Math.min(sh.getColumnWidth(c) + 512, 255 * 256));
            }
        }
    }

    private void initColorMap() {
        colorMap.put("bg-gray-100", IndexedColors.GREY_25_PERCENT.getIndex());
        colorMap.put("bg-gray-200", IndexedColors.GREY_40_PERCENT.getIndex());
        colorMap.put("bg-gray-300", IndexedColors.GREY_50_PERCENT.getIndex());
        colorMap.put("bg-emerald-100", IndexedColors.LIGHT_GREEN.getIndex());
        colorMap.put("bg-emerald-200", IndexedColors.GREEN.getIndex());
        colorMap.put("bg-rose-100", IndexedColors.ROSE.getIndex());
    }

    private Short resolveColor(String tailwindLike) {
        if (tailwindLike == null) return null;
        return colorMap.get(tailwindLike);
    }
}
