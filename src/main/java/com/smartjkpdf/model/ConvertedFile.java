package com.smartjkpdf.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;

public class ConvertedFile {
    private String id;
    private String originalName;
    private String convertedName;
    private String sourceFormat;
    private String targetFormat;
    private long fileSize;
    private String filePath;

    @JsonFormat(pattern = "dd/MM/yyyy HH:mm:ss")
    private LocalDateTime conversionDate;

    public ConvertedFile() {}

    public ConvertedFile(String id, String originalName, String convertedName,
                         String sourceFormat, String targetFormat,
                         long fileSize, String filePath, LocalDateTime conversionDate) {
        this.id = id;
        this.originalName = originalName;
        this.convertedName = convertedName;
        this.sourceFormat = sourceFormat;
        this.targetFormat = targetFormat;
        this.fileSize = fileSize;
        this.filePath = filePath;
        this.conversionDate = conversionDate;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getOriginalName() { return originalName; }
    public void setOriginalName(String originalName) { this.originalName = originalName; }

    public String getConvertedName() { return convertedName; }
    public void setConvertedName(String convertedName) { this.convertedName = convertedName; }

    public String getSourceFormat() { return sourceFormat; }
    public void setSourceFormat(String sourceFormat) { this.sourceFormat = sourceFormat; }

    public String getTargetFormat() { return targetFormat; }
    public void setTargetFormat(String targetFormat) { this.targetFormat = targetFormat; }

    public long getFileSize() { return fileSize; }
    public void setFileSize(long fileSize) { this.fileSize = fileSize; }

    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }

    public LocalDateTime getConversionDate() { return conversionDate; }
    public void setConversionDate(LocalDateTime conversionDate) { this.conversionDate = conversionDate; }

    public String getFormattedSize() {
        if (fileSize < 1024) return fileSize + " B";
        else if (fileSize < 1024 * 1024) return String.format("%.1f KB", fileSize / 1024.0);
        else return String.format("%.1f MB", fileSize / (1024.0 * 1024));
    }
}
