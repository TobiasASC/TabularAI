package com.tabularai.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.tabularai.model.ColumnDataType;
import com.tabularai.model.ColumnProfile;

class StatsEngineServiceTest {

    private StatsEngineService statsEngineService;

    @BeforeEach
    void setUp() {
        statsEngineService = new StatsEngineService();
    }

    @Test
    @DisplayName("Debe inferir tipos de datos y calcular métricas exactas en un CSV mixto con cantidad impar de filas")
    void shouldAnalyzeMixedCsvWithOddRows() throws IOException {
        String csv = """
                precio,categoria,activo,fecha
                100.0,Electronica,true,2026-01-10
                200.0,Hogar,false,2026-01-11
                300.0,Electronica,true,2026-01-12
                400.0,Ropa,true,2026-01-13
                1000.0,Electronica,false,2026-01-14
                """;

        List<ColumnProfile> profiles = statsEngineService.analyze(toInputStream(csv));

        assertEquals(4, profiles.size());

        // 1. Columna NUMERIC (precio): [100, 200, 300, 400, 1000]
        ColumnProfile precioProfile = profiles.get(0);
        assertEquals("precio", precioProfile.getColumnName());
        assertEquals(ColumnDataType.NUMERIC, precioProfile.getDataType());
        assertEquals(0, precioProfile.getNullCount());
        assertEquals(5, precioProfile.getUniqueCount());
        assertEquals(100.0, precioProfile.getMinVal(), 0.001);
        assertEquals(1000.0, precioProfile.getMaxVal(), 0.001);
        assertEquals(400.0, precioProfile.getMeanVal(), 0.001);
        assertEquals(300.0, precioProfile.getMedianVal(), 0.001);
        assertNull(precioProfile.getTopValues());

        // 2. Columna TEXT (categoria): Electronica (3), Hogar (1), Ropa (1)
        ColumnProfile categoriaProfile = profiles.get(1);
        assertEquals("categoria", categoriaProfile.getColumnName());
        assertEquals(ColumnDataType.TEXT, categoriaProfile.getDataType());
        assertEquals(0, categoriaProfile.getNullCount());
        assertEquals(3, categoriaProfile.getUniqueCount());
        assertNull(categoriaProfile.getMinVal());
        assertNotNull(categoriaProfile.getTopValues());
        assertEquals(3L, categoriaProfile.getTopValues().get("Electronica"));
        assertEquals(1L, categoriaProfile.getTopValues().get("Hogar"));
        assertEquals(1L, categoriaProfile.getTopValues().get("Ropa"));

        // 3. Columna BOOLEAN (activo): true (3), false (2)
        ColumnProfile activoProfile = profiles.get(2);
        assertEquals("activo", activoProfile.getColumnName());
        assertEquals(ColumnDataType.BOOLEAN, activoProfile.getDataType());
        assertEquals(0, activoProfile.getNullCount());
        assertEquals(2, activoProfile.getUniqueCount());
        assertEquals(3L, activoProfile.getTopValues().get("true"));
        assertEquals(2L, activoProfile.getTopValues().get("false"));

        // 4. Columna DATE (fecha)
        ColumnProfile fechaProfile = profiles.get(3);
        assertEquals("fecha", fechaProfile.getColumnName());
        assertEquals(ColumnDataType.DATE, fechaProfile.getDataType());
        assertEquals(0, fechaProfile.getNullCount());
        assertEquals(5, fechaProfile.getUniqueCount());
    }

    @Test
    @DisplayName("Debe ignorar celdas vacías y calcular correctamente la mediana con cantidad par de valores numéricos")
    void shouldHandleEmptyCellsAndEvenMedian() throws IOException {
        String csv = """
                edad,ciudad
                20,Buenos Aires
                ,Cordoba
                30,
                40,Buenos Aires
                50,Rosario
                ,
                """;

        List<ColumnProfile> profiles = statsEngineService.analyze(toInputStream(csv));

        assertEquals(2, profiles.size());

        // Columna edad: 4 valores válidos [20, 30, 40, 50] y 2 vacíos
        ColumnProfile edadProfile = profiles.get(0);
        assertEquals(ColumnDataType.NUMERIC, edadProfile.getDataType());
        assertEquals(2, edadProfile.getNullCount());
        assertEquals(4, edadProfile.getUniqueCount());
        assertEquals(20.0, edadProfile.getMinVal(), 0.001);
        assertEquals(50.0, edadProfile.getMaxVal(), 0.001);
        assertEquals(35.0, edadProfile.getMeanVal(), 0.001);
        assertEquals(35.0, edadProfile.getMedianVal(), 0.001); // (30 + 40) / 2 = 35.0

        // Columna ciudad: 4 valores válidos y 2 vacíos
        ColumnProfile ciudadProfile = profiles.get(1);
        assertEquals(ColumnDataType.TEXT, ciudadProfile.getDataType());
        assertEquals(2, ciudadProfile.getNullCount());
        assertEquals(3, ciudadProfile.getUniqueCount());
        assertEquals(2L, ciudadProfile.getTopValues().get("Buenos Aires"));
    }

    @Test
    @DisplayName("Debe limitar topValues a los 5 valores más frecuentes ordenados de mayor a menor")
    void shouldLimitTopValuesToFiveOrderedByFrequency() throws IOException {
        String csv = """
                producto
                A
                A
                A
                A
                B
                B
                B
                C
                C
                D
                E
                F
                G
                """;

        List<ColumnProfile> profiles = statsEngineService.analyze(toInputStream(csv));
        ColumnProfile productoProfile = profiles.getFirst();

        assertEquals(ColumnDataType.TEXT, productoProfile.getDataType());
        assertEquals(7, productoProfile.getUniqueCount());

        Map<String, Long> topValues = productoProfile.getTopValues();
        assertEquals(5, topValues.size());

        List<String> keys = new ArrayList<>(topValues.keySet());
        assertEquals("A", keys.get(0));
        assertEquals(4L, topValues.get("A"));
        assertEquals("B", keys.get(1));
        assertEquals(3L, topValues.get("B"));
        assertEquals("C", keys.get(2));
        assertEquals(2L, topValues.get("C"));
    }

    @Test
    @DisplayName("Debe manejar columnas completamente vacías y filas con columnas faltantes sin lanzar excepciones")
    void shouldHandleCompletelyEmptyColumnsAndIncompleteRows() throws IOException {
        String csv = """
                nombre,observaciones
                Juan,
                Maria
                Pedro,   
                """;

        List<ColumnProfile> profiles = statsEngineService.analyze(toInputStream(csv));

        assertEquals(2, profiles.size());

        ColumnProfile observacionesProfile = profiles.get(1);
        assertEquals("observaciones", observacionesProfile.getColumnName());
        assertEquals(ColumnDataType.TEXT, observacionesProfile.getDataType());
        assertEquals(3, observacionesProfile.getNullCount());
        assertEquals(0, observacionesProfile.getUniqueCount());
        assertTrue(observacionesProfile.getTopValues().isEmpty());
    }

    private InputStream toInputStream(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }
}
