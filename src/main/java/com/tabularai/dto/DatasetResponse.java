package com.tabularai.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.tabularai.model.Dataset;
import com.tabularai.model.DatasetStatus;

/**
 * DTO de respuesta completa de un dataset: incluye sus metadatos,
 * todos sus perfiles estadísticos por columna y todos sus reportes de IA.
 */
public record DatasetResponse(
        Long id,
        String filename,
        Long fileSizeBytes,
        int rowCount,
        int columnCount,
        DatasetStatus status,
        LocalDateTime createdAt,
        List<ColumnProfileDto> columnProfiles,
        List<AiReportDto> aiReports
) {
    public static DatasetResponse fromEntity(Dataset entity) {
        List<ColumnProfileDto> profiles = entity.getColumnProfiles().stream()
                .map(ColumnProfileDto::fromEntity)
                .toList();

        List<AiReportDto> reports = entity.getAiReports().stream()
                .map(AiReportDto::fromEntity)
                .toList();

        return new DatasetResponse(
                entity.getId(),
                entity.getFilename(),
                entity.getFileSizeBytes(),
                entity.getRowCount(),
                entity.getColumnCount(),
                entity.getStatus(),
                entity.getCreatedAt(),
                profiles,
                reports
        );
    }
}
