package com.tabularai.dto;

import java.util.Map;

import com.tabularai.model.ColumnDataType;
import com.tabularai.model.ColumnProfile;

/**
 * DTO de respuesta que expone las métricas estadísticas de una columna calculadas por Java.
 */
public record ColumnProfileDto(
        Long id,
        String columnName,
        ColumnDataType dataType,
        int nullCount,
        int uniqueCount,
        Double minVal,
        Double maxVal,
        Double meanVal,
        Double medianVal,
        Map<String, Long> topValues
) {
    public static ColumnProfileDto fromEntity(ColumnProfile entity) {
        return new ColumnProfileDto(
                entity.getId(),
                entity.getColumnName(),
                entity.getDataType(),
                entity.getNullCount(),
                entity.getUniqueCount(),
                entity.getMinVal(),
                entity.getMaxVal(),
                entity.getMeanVal(),
                entity.getMedianVal(),
                entity.getTopValues()
        );
    }
}
