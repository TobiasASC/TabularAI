package com.tabularai.dto;

import java.util.List;

/**
 * DTO inmutable (Java Record) utilizado por Spring AI (Structured Outputs)
 * para convertir automáticamente la respuesta JSON del modelo de IA en un objeto Java.
 *
 * @param executiveSummary Resumen ejecutivo en lenguaje natural sobre el dataset analizado.
 * @param findings         Lista de hallazgos clave, anomalías o recomendaciones detectadas.
 */
public record AiInsightsDto(
        String executiveSummary,
        List<String> findings
) {
}
