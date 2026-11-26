package com.tabularai.model;

import java.time.LocalDateTime;
import java.util.List;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Cada instancia de este model representa un análisis generado por el LLM sobre un dataset.
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "ai_reports")
public class AiReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ai_report_id")
    private Long id;

    // Relación N:1 con Dataset, un dataset tiene multiples reportes de IA sin necesidad de volver a subir un CSV
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dataset_id", nullable = false)
    private Dataset dataset;

    // Guarda el contexto, lo que pidio el usuario
    @Column(name = "user_context", length = 500)
    private String userContext;

    // Guarda el modelo de IA usado en el reporte
    @Column(name = "model_used", length = 100)
    private String modelUsed;

    // Resumen ejecutivo del LLM
    @Column(name = "executive_summary", columnDefinition = "TEXT")
    private String executiveSummary;

    // Se guardan los hallazgos en una lista
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "findings", columnDefinition = "jsonb")
    private List<String> findings;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

}
