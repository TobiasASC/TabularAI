package com.tabularai.dto;

import java.time.LocalDateTime;

import com.tabularai.model.Dataset;
import com.tabularai.model.DatasetStatus;

/**
 * DTO liviano para listar el historial de datasets cargados (solo metadatos, sin perfiles ni reportes pesados).
 */
public record DatasetSummaryDto(
        Long id,
        String filename,
        Long fileSizeBytes,
        int rowCount,
        int columnCount,
        DatasetStatus status,
        LocalDateTime createdAt
) {
    public static DatasetSummaryDto fromEntity(Dataset entity) {
        return new DatasetSummaryDto(
                entity.getId(),
                entity.getFilename(),
                entity.getFileSizeBytes(),
                entity.getRowCount(),
                entity.getColumnCount(),
                entity.getStatus(),
                entity.getCreatedAt()
        );
    }
}
