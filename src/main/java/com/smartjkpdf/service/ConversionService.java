package com.smartjkpdf.service;

import com.smartjkpdf.model.ConvertedFile;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class ConversionService {

    @Value("${app.storage.dir:./converted-files}")
    private String storageDir;

    private final List<ConvertedFile> conversionHistory = new ArrayList<>();

    /* ── chemin absolu du répertoire de stockage ── */
    private Path getStoragePath() throws IOException {
        Path p = Paths.get(storageDir).toAbsolutePath().normalize();
        if (!Files.exists(p)) Files.createDirectories(p);
        return p;
    }

    /* ── chemin absolu d'un fichier stocké ── */
    public Path getAbsoluteFilePath(String filename) throws IOException {
        Path base = getStoragePath();
        Path p    = base.resolve(filename).normalize();
        if (!p.startsWith(base))
            throw new SecurityException("Path traversal détecté: " + filename);
        return p;
    }

    /* ── recherche par ID ── */
    public ConvertedFile findById(String id) {
        return conversionHistory.stream()
            .filter(f -> f.getId().equals(id))
            .findFirst().orElse(null);
    }

    /* ════════════════════════════════════════════
       CONVERSION PRINCIPALE
    ════════════════════════════════════════════ */
    public ConvertedFile convertFile(MultipartFile file, String targetFormat) throws IOException {
        Path storage = getStoragePath();

        String originalName = file.getOriginalFilename();
        if (originalName == null || originalName.isBlank()) originalName = "fichier";

        String sourceExt = getExtension(originalName).toLowerCase();
        String id        = UUID.randomUUID().toString();
        String safeName  = getBaseName(originalName).replaceAll("[^a-zA-Z0-9_-]", "_");
        String outName   = safeName + "_" + id.substring(0, 8) + "." + targetFormat.toLowerCase();
        Path   outPath   = storage.resolve(outName);

        byte[] bytes = file.getBytes();

        switch (targetFormat.toLowerCase()) {
            case "pdf"  -> convertToPdf(bytes, sourceExt, outPath);
            case "docx" -> convertToWord(bytes, sourceExt, outPath);
            case "xlsx" -> convertToExcel(bytes, sourceExt, outPath);
            default     -> throw new IllegalArgumentException("Format non supporté: " + targetFormat);
        }

        System.out.println("[SmartJKPDF] Fichier créé: " + outPath + " (" + Files.size(outPath) + " bytes)");

        ConvertedFile cf = new ConvertedFile(
            id, originalName, outName, sourceExt,
            targetFormat.toLowerCase(), Files.size(outPath),
            outPath.toString(), LocalDateTime.now()
        );
        conversionHistory.add(0, cf);
        return cf;
    }

    /* ════════════════════════════════════════════
       CONVERSIONS
    ════════════════════════════════════════════ */
    private void convertToPdf(byte[] bytes, String srcExt, Path out) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            String text = extractText(bytes, srcExt);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
                PDType1Font norm = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
                float m = 50, y = PDRectangle.A4.getHeight() - m, lead = 15;
                cs.beginText(); cs.setFont(bold, 13); cs.newLineAtOffset(m, y);
                cs.showText("Converti par SmartJKPDF"); cs.endText();
                y -= lead * 2;
                cs.beginText(); cs.setFont(norm, 10); cs.newLineAtOffset(m, y);
                for (String line : text.split("\n")) {
                    if (y < m + lead) break;
                    String s = sanitize(line.length() > 100 ? line.substring(0, 100) + "..." : line);
                    cs.showText(s); cs.newLineAtOffset(0, -lead); y -= lead;
                }
                cs.endText();
            }
            doc.save(out.toFile());
        }
    }

    private void convertToWord(byte[] bytes, String srcExt, Path out) throws IOException {
        try (XWPFDocument doc = new XWPFDocument()) {
            XWPFParagraph t = doc.createParagraph();
            t.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun r = t.createRun();
            r.setText("Document converti par SmartJKPDF"); r.setBold(true); r.setFontSize(16);
            doc.createParagraph();
            for (String line : extractText(bytes, srcExt).split("\n")) {
                XWPFRun run = doc.createParagraph().createRun();
                run.setText(line.isBlank() ? " " : line); run.setFontSize(11);
            }
            try (FileOutputStream fos = new FileOutputStream(out.toFile())) { doc.write(fos); }
        }
    }

    private void convertToExcel(byte[] bytes, String srcExt, Path out) throws IOException {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sh = wb.createSheet("SmartJKPDF");
            CellStyle hs = wb.createCellStyle();
            Font hf = wb.createFont(); hf.setBold(true); hf.setColor(IndexedColors.WHITE.getIndex());
            hs.setFont(hf); hs.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            hs.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            Row h = sh.createRow(0);
            Cell h0 = h.createCell(0); h0.setCellValue("Ligne");   h0.setCellStyle(hs);
            Cell h1 = h.createCell(1); h1.setCellValue("Contenu"); h1.setCellStyle(hs);
            String[] lines = extractText(bytes, srcExt).split("\n");
            for (int i = 0; i < lines.length && i < 5000; i++) {
                Row row = sh.createRow(i + 1);
                row.createCell(0).setCellValue(i + 1);
                row.createCell(1).setCellValue(lines[i]);
            }
            sh.autoSizeColumn(0); sh.setColumnWidth(1, 25000);
            try (FileOutputStream fos = new FileOutputStream(out.toFile())) { wb.write(fos); }
        }
    }

    /* ════════════════════════════════════════════
       EXTRACTION TEXTE
    ════════════════════════════════════════════ */
    private String extractText(byte[] bytes, String ext) throws IOException {
        return switch (ext) {
            case "pdf" -> {
                try (PDDocument d = Loader.loadPDF(bytes)) { yield new PDFTextStripper().getText(d); }
            }
            case "docx", "odt" -> {
                try (XWPFDocument d = new XWPFDocument(new ByteArrayInputStream(bytes))) {
                    StringBuilder sb = new StringBuilder();
                    for (XWPFParagraph p : d.getParagraphs()) sb.append(p.getText()).append("\n");
                    yield sb.toString();
                }
            }
            case "xlsx", "xls", "ods" -> {
                try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
                    StringBuilder sb = new StringBuilder();
                    for (Sheet s : wb) {
                        sb.append("=== ").append(s.getSheetName()).append(" ===\n");
                        for (Row row : s) {
                            List<String> cells = new ArrayList<>();
                            for (Cell c : row) cells.add(cellStr(c));
                            sb.append(String.join("\t", cells)).append("\n");
                        }
                    }
                    yield sb.toString();
                }
            }
            default -> new String(bytes, StandardCharsets.UTF_8);
        };
    }

    /* ════════════════════════════════════════════
       APERÇU
    ════════════════════════════════════════════ */
    public String getPreviewContent(String filename) throws IOException {
        Path fp = getAbsoluteFilePath(filename);
        if (!Files.exists(fp)) throw new FileNotFoundException("Introuvable: " + filename);
        return switch (getExtension(filename).toLowerCase()) {
            case "pdf" -> {
                try (PDDocument d = Loader.loadPDF(fp.toFile())) {
                    PDFTextStripper s = new PDFTextStripper(); s.setEndPage(3);
                    yield s.getText(d);
                }
            }
            case "docx" -> {
                try (XWPFDocument d = new XWPFDocument(Files.newInputStream(fp))) {
                    StringBuilder sb = new StringBuilder(); int n = 0;
                    for (XWPFParagraph p : d.getParagraphs()) {
                        if (n++ > 60) break; sb.append(p.getText()).append("\n");
                    }
                    yield sb.toString();
                }
            }
            case "xlsx" -> {
                try (Workbook wb = WorkbookFactory.create(fp.toFile())) {
                    StringBuilder sb = new StringBuilder(); int rc = 0;
                    for (Row row : wb.getSheetAt(0)) {
                        if (rc++ > 25) break;
                        List<String> cells = new ArrayList<>();
                        for (Cell c : row) cells.add(cellStr(c));
                        sb.append(String.join("|", cells)).append("\n");
                    }
                    yield sb.toString();
                }
            }
            default -> new String(Files.readAllBytes(fp), StandardCharsets.UTF_8);
        };
    }

    /* ════════════════════════════════════════════
       HISTORIQUE / SUPPRESSION
    ════════════════════════════════════════════ */
    public List<ConvertedFile> getHistory() {
        return Collections.unmodifiableList(conversionHistory);
    }

    public boolean deleteFile(String id) throws IOException {
        ConvertedFile f = findById(id);
        if (f == null) return false;
        Files.deleteIfExists(getAbsoluteFilePath(f.getConvertedName()));
        conversionHistory.removeIf(x -> x.getId().equals(id));
        return true;
    }

    public void deleteAllFiles() throws IOException {
        for (ConvertedFile f : new ArrayList<>(conversionHistory))
            Files.deleteIfExists(getAbsoluteFilePath(f.getConvertedName()));
        conversionHistory.clear();
    }

    /* ════════════════════════════════════════════
       UTILS
    ════════════════════════════════════════════ */
    private String cellStr(Cell c) {
        if (c == null) return "";
        return switch (c.getCellType()) {
            case STRING  -> c.getStringCellValue();
            case NUMERIC -> String.valueOf(c.getNumericCellValue());
            case BOOLEAN -> String.valueOf(c.getBooleanCellValue());
            case FORMULA -> c.getCellFormula();
            default      -> "";
        };
    }

    private String sanitize(String s) {
        if (s == null) return "";
        return s.replaceAll("[^\\x20-\\x7E]", " ");
    }

    private String getExtension(String name) {
        if (name == null || !name.contains(".")) return "";
        return name.substring(name.lastIndexOf('.') + 1);
    }

    private String getBaseName(String name) {
        if (name == null) return "file";
        int i = name.lastIndexOf('.');
        return i > 0 ? name.substring(0, i) : name;
    }

}
