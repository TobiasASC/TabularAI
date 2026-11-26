package com.tabularai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO de entrada para solicitar un nuevo reporte de IA sobre un dataset ya existente
 * (POST /api/v1/datasets/{id}/reports).
 */
public record CreateReportRequest(
        @NotBlank(message = "El contexto o enfoque para el reporte no puede estar vacío.")
        @Size(max = 500, message = "El contexto no puede superar los 500 caracteres.")
        String context
) {
}
