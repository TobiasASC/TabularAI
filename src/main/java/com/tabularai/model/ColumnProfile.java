package com.tabularai.model;

import java.util.Map;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "column_profiles")
public class ColumnProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "column_profile_id")
    private Long id;

    /* @JoinColumn(name = "dataset_id", nullable = false): Crea físicamente la columna de clave foránea dataset_id en la tabla column_profiles. 
    Ningún perfil puede existir sin pertenecer a un Dataset*/
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dataset_id", nullable = false)
    private Dataset dataset;

    @Column(name = "column_name", nullable = false)
    private String columnName;

    @Enumerated(EnumType.STRING)
    @Column(name = "data_type", nullable = false, columnDefinition = "varchar(30)")
    private ColumnDataType dataType;

    @Column(name = "null_count")
    private int nullCount;

    @Column(name = "unique_count")
    private int uniqueCount;

    @Column(name = "min_val")
    private Double minVal;

    @Column(name = "max_val")
    private Double maxVal;

    @Column(name = "mean_val")
    private Double meanVal;

    @Column(name = "median_val")
    private Double medianVal;

    // Campo de los 5 valores más repetidos y su frencuencia de las columnas que no son numéricas
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "top_values", columnDefinition = "jsonb")
    private Map<String, Long> topValues;
}
