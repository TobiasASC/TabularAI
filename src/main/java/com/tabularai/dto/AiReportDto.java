package com.tabularai.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.tabularai.model.AiReport;

/**
 * DTO de respuesta que expone un reporte diagnóstico generado por la IA sobre un dataset.
 */
public record AiReportDto(
        Long id,
        String userContext,
        String modelUsed,
        String executiveSummary,
        List<String> findings,
        LocalDateTime createdAt
) {
    public static AiReportDto fromEntity(AiReport entity) {
        return new AiReportDto(
                entity.getId(),
                entity.getUserContext(),
                entity.getModelUsed(),
                entity.getExecutiveSummary(),
                entity.getFindings(),
                entity.getCreatedAt()
        );
    }
}
