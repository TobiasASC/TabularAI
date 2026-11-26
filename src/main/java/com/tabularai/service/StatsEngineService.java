package com.tabularai.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;

import com.tabularai.model.ColumnDataType;
import com.tabularai.model.ColumnProfile;

@Service
public class StatsEngineService {

    // Cantidad máxima de valores frecuentes a guardar en columnas no numéricas
    private static final int TOP_VALUES_LIMIT = 5;

    // Formatos de fecha soportados para la detección automática de columnas tipo DATE
    private static final List<DateTimeFormatter> DATE_FORMATTERS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd")
    );

    /**
     * Lee un archivo CSV desde un InputStream, agrupa sus valores por columna (transposición)
     * y genera el perfil estadístico (ColumnProfile) de cada una.
     */
    public List<ColumnProfile> analyze(InputStream inputStream) throws IOException {
        // LinkedHashMap mantiene el orden original en que aparecen las columnas en el CSV
        Map<String, List<String>> columnsData = new LinkedHashMap<>();

        CSVFormat format = buildCsvFormat();

        try (Reader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
             CSVParser parser = format.parse(reader)) {

            List<String> headers = parser.getHeaderNames();

            // 1. Inicializamos una lista vacía para cada columna detectada en el encabezado
            for (String header : headers) {
                columnsData.put(header, new ArrayList<>());
            }

            // 2. Recorremos fila por fila y distribuimos cada celda en la lista de su columna
            for (CSVRecord record : parser) {
                for (String header : headers) {
                    String value = record.isSet(header) ? record.get(header) : null;
                    columnsData.get(header).add(value);
                }
            }
        }

        // 3. Procesamos cada columna por separado para construir su ColumnProfile
        List<ColumnProfile> profiles = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : columnsData.entrySet()) {
            ColumnProfile profile = buildColumnProfile(entry.getKey(), entry.getValue());
            profiles.add(profile);
        }

        return profiles;
    }

    /**
     * Cuenta el total de filas de datos (excluyendo el encabezado y líneas vacías) del archivo CSV.
     */
    public int countRows(InputStream inputStream) throws IOException {
        CSVFormat format = buildCsvFormat();
        int rowCount = 0;

        try (Reader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
             CSVParser parser = format.parse(reader)) {
            for (CSVRecord ignored : parser) {
                rowCount++;
            }
        }

        return rowCount;
    }

    /**
     * Configuración estándar de Apache Commons CSV para todo el motor estadístico.
     */
    private CSVFormat buildCsvFormat() {
        return CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreEmptyLines(true)
                .setTrim(true)
                .build();
    }

    /**
     * Construye el perfil completo de una columna: separa valores nulos/vacíos de los válidos,
     * cuenta los valores únicos, infiere el tipo de dato y calcula sus métricas (numéricas o Top 5).
     */
    private ColumnProfile buildColumnProfile(String columnName, List<String> values) {
        ColumnProfile profile = new ColumnProfile();
        profile.setColumnName(columnName);

        List<String> validValues = new ArrayList<>();
        int nullCount = 0;

        // Filtramos celdas nulas o en blanco para no distorsionar la inferencia de tipos ni los cálculos
        for (String value : values) {
            if (value == null || value.isBlank()) {
                nullCount++;
            } else {
                validValues.add(value.trim());
            }
        }

        profile.setNullCount(nullCount);
        // HashSet elimina duplicados automáticamente; su tamaño nos da la cantidad de valores únicos
        profile.setUniqueCount(new HashSet<>(validValues).size());

        ColumnDataType dataType = inferDataType(validValues);
        profile.setDataType(dataType);

        // Si es numérica calculamos min/max/promedio/mediana; en caso contrario calculamos el Top 5 de frecuencias
        if (dataType == ColumnDataType.NUMERIC && !validValues.isEmpty()) {
            calculateNumericStats(profile, validValues);
        } else if (!validValues.isEmpty()) {
            profile.setTopValues(calculateTopValues(validValues, TOP_VALUES_LIMIT));
        } else {
            profile.setTopValues(Map.of());
        }

        return profile;
    }

    /**
     * Infiere el tipo de dato de la columna evaluando todos sus valores no vacíos.
     * Solo clasifica como BOOLEAN, NUMERIC o DATE si el 100% de los valores válidos cumplen ese formato;
     * de lo contrario (o si la columna está completamente vacía), la clasifica como TEXT.
     */
    private ColumnDataType inferDataType(List<String> validValues) {
        if (validValues.isEmpty()) {
            return ColumnDataType.TEXT;
        }

        if (validValues.stream().allMatch(this::isBoolean)) {
            return ColumnDataType.BOOLEAN;
        }

        if (validValues.stream().allMatch(this::isNumeric)) {
            return ColumnDataType.NUMERIC;
        }

        if (validValues.stream().allMatch(this::isDate)) {
            return ColumnDataType.DATE;
        }

        return ColumnDataType.TEXT;
    }

    /**
     * Verifica si un texto representa un valor booleano ("true" o "false", sin importar mayúsculas/minúsculas).
     */
    private boolean isBoolean(String value) {
        return "true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value);
    }

    /**
     * Verifica si un texto puede convertirse en un número decimal válido y finito (descarta NaN o Infinito).
     */
    private boolean isNumeric(String value) {
        try {
            double parsed = Double.parseDouble(value);
            return Double.isFinite(parsed);
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * Verifica si un texto coincide con alguno de los formatos de fecha (LocalDate) o fecha-hora (ISO LocalDateTime) soportados.
     */
    private boolean isDate(String value) {
        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                LocalDate.parse(value, formatter);
                return true;
            } catch (DateTimeParseException ignored) {
                // Si no coincide con este formato, probamos con el siguiente de la lista
            }
        }
        try {
            LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    /**
     * Convierte los valores de texto a Double, los ordena de menor a mayor y calcula
     * las métricas exactas de una columna numérica: mínimo, máximo, promedio (mean) y mediana (median).
     */
    private void calculateNumericStats(ColumnProfile profile, List<String> validValues) {
        List<Double> numbers = validValues.stream()
                .map(Double::parseDouble)
                .sorted()
                .toList();

        int n = numbers.size();
        double min = numbers.getFirst();
        double max = numbers.getLast();
        double sum = numbers.stream().mapToDouble(Double::doubleValue).sum();
        double mean = sum / n;

        // Si la cantidad de elementos es impar, la mediana es el valor central;
        // si es par, es el promedio de los dos valores centrales.
        double median;
        if (n % 2 == 1) {
            median = numbers.get(n / 2);
        } else {
            median = (numbers.get((n / 2) - 1) + numbers.get(n / 2)) / 2.0;
        }

        profile.setMinVal(min);
        profile.setMaxVal(max);
        profile.setMeanVal(mean);
        profile.setMedianVal(median);
    }

    /**
     * Cuenta cuántas veces se repite cada valor en la columna y devuelve un mapa ordenado
     * de mayor a menor frecuencia con los N valores más repetidos (Top Values).
     */
    private Map<String, Long> calculateTopValues(List<String> validValues, int limit) {
        // 1. Agrupamos por valor idéntico y contamos cuántas veces aparece cada uno
        Map<String, Long> frequencies = validValues.stream()
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

        // 2. Ordenamos por frecuencia descendente (y alfabéticamente en caso de empate),
        //    limitamos al top N y guardamos en un LinkedHashMap para preservar ese orden.
        return frequencies.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(Map.Entry.comparingByKey()))
                .limit(limit)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (a, b) -> a,
                        LinkedHashMap::new
                ));
    }
}
