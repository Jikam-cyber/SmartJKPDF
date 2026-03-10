package com.smartjkpdf.controller;

import com.smartjkpdf.model.ConvertedFile;
import com.smartjkpdf.service.ConversionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@Controller
public class WebController {

    @Autowired
    private ConversionService conversionService;

    /* ── 1. PAGE PRINCIPALE ── */
    @GetMapping("/")
    public String index() {
        return "index";
    }

    /* ── 2. CONVERSION ── */
    @PostMapping("/api/convert")
    @ResponseBody
    public ResponseEntity<?> convert(
            @RequestParam("file") MultipartFile file,
            @RequestParam("format") String format) {
        try {
            ConvertedFile result = conversionService.convertFile(file, format);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Fichier converti en " + format.toUpperCase() + " !",
                "file", result
            ));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }

    /* ── 3. LISTE HISTORIQUE ── */
    @GetMapping("/api/files")
    @ResponseBody
    public List<ConvertedFile> getHistory() {
        return conversionService.getHistory();
    }

    /* ── 4. TÉLÉCHARGEMENT — par ID ── */
    @GetMapping("/api/files/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable String id) {
        return serveById(id, true);
    }

    /* ── 5. APERÇU TEXTE/EXCEL — par ID ── */
    @GetMapping("/api/files/{id}/preview")
    @ResponseBody
    public ResponseEntity<?> preview(@PathVariable String id) {
        ConvertedFile f = conversionService.findById(id);
        if (f == null)
            return ResponseEntity.status(404).body(Map.of("error", "Fichier introuvable"));
        try {
            String content = conversionService.getPreviewContent(f.getConvertedName());
            return ResponseEntity.ok(Map.of(
                "filename", f.getConvertedName(),
                "content",  content,
                "format",   f.getTargetFormat()
            ));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    /* ── 6. VUE INLINE PDF — par ID ── */
    @GetMapping("/api/files/{id}/view")
    public ResponseEntity<Resource> view(@PathVariable String id) {
        return serveById(id, false);
    }

    /* ── 7. SUPPRESSION UNITAIRE ── */
    @DeleteMapping("/api/files/{id}")
    @ResponseBody
    public ResponseEntity<?> delete(@PathVariable String id) {
        try {
            boolean deleted = conversionService.deleteFile(id);
            return deleted
                ? ResponseEntity.ok(Map.of("success", true))
                : ResponseEntity.status(404).body(Map.of("error", "Non trouvé"));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    /* ── 8. SUPPRESSION TOTALE ── */
    @DeleteMapping("/api/files")
    @ResponseBody
    public ResponseEntity<?> deleteAll() {
        try {
            conversionService.deleteAllFiles();
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    /* ── INTERNE — sert un fichier par ID ── */
    private ResponseEntity<Resource> serveById(String id, boolean download) {
        ConvertedFile f = conversionService.findById(id);
        if (f == null) return ResponseEntity.notFound().build();
        try {
            Path filePath = conversionService.getAbsoluteFilePath(f.getConvertedName());
            if (!Files.exists(filePath)) return ResponseEntity.notFound().build();

            Resource resource = new UrlResource(filePath.toUri());
            if (!resource.isReadable()) return ResponseEntity.status(500).build();

            String ext = f.getConvertedName().contains(".")
                ? f.getConvertedName().substring(f.getConvertedName().lastIndexOf('.') + 1).toLowerCase()
                : "";
            String contentType = switch (ext) {
                case "pdf"  -> "application/pdf";
                case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
                case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
                default     -> "application/octet-stream";
            };
            String disp = download ? "attachment" : "inline";
            String enc  = URLEncoder.encode(f.getConvertedName(), StandardCharsets.UTF_8)
                            .replace("+", "%20");

            return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .contentLength(Files.size(filePath))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                    disp + "; filename=\"" + f.getConvertedName() + "\"; filename*=UTF-8''" + enc)
                .header(HttpHeaders.CACHE_CONTROL, "no-cache")
                .body(resource);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).build();
        }
    }
}