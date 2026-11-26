package com.tabularai.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
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

    // Carácter invisible BOM (Byte Order Mark) que suelen agregar SQL Server Management Studio y Excel al inicio del CSV
    private static final String UTF8_BOM = "\uFEFF";

    // Formatos de fecha soportados para la detección automática de columnas tipo DATE
    private static final List<DateTimeFormatter> DATE_FORMATTERS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd")
    );

    /**
     * Lee un archivo CSV desde un InputStream, detecta automáticamente su delimitador (',', ';' o '\t'),
     * agrupa sus valores por columna (transposición) y genera el perfil estadístico (ColumnProfile) de cada una.
     */
    public List<ColumnProfile> analyze(InputStream inputStream) throws IOException {
        String csvContent = readAndCleanContent(inputStream);
        char delimiter = detectDelimiter(csvContent);
        CSVFormat format = buildCsvFormat(delimiter);

        // LinkedHashMap mantiene el orden original en que aparecen las columnas en el CSV
        Map<String, List<String>> columnsData = new LinkedHashMap<>();

        try (Reader reader = new StringReader(csvContent);
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
     * Cuenta el total de filas de datos (excluyendo el encabezado y líneas vacías) del archivo CSV,
     * respetando el delimitador detectado automáticamente.
     */
    public int countRows(InputStream inputStream) throws IOException {
        String csvContent = readAndCleanContent(inputStream);
        char delimiter = detectDelimiter(csvContent);
        CSVFormat format = buildCsvFormat(delimiter);
        int rowCount = 0;

        try (Reader reader = new StringReader(csvContent);
             CSVParser parser = format.parse(reader)) {
            for (CSVRecord ignored : parser) {
                rowCount++;
            }
        }

        return rowCount;
    }

    /**
     * Convierte el InputStream a String UTF-8 y elimina el carácter invisible BOM (\uFEFF)
     * si fue exportado desde SQL Server Management Studio o Excel.
     */
    private String readAndCleanContent(InputStream inputStream) throws IOException {
        String content = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        if (content.startsWith(UTF8_BOM)) {
            return content.substring(1);
        }
        return content;
    }

    /**
     * Inspecciona la primera línea no vacía del CSV (encabezado) y detecta qué separador de columnas
     * predomina: coma (','), punto y coma (';' usado por SQL Server / Excel en español) o tabulador ('\t').
     */
    private char detectDelimiter(String csvContent) throws IOException {
        try (BufferedReader br = new BufferedReader(new StringReader(csvContent))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (!line.isBlank()) {
                    long commas = line.chars().filter(ch -> ch == ',').count();
                    long semicolons = line.chars().filter(ch -> ch == ';').count();
                    long tabs = line.chars().filter(ch -> ch == '\t').count();

                    if (semicolons > commas && semicolons >= tabs) {
                        return ';';
                    }
                    if (tabs > commas && tabs > semicolons) {
                        return '\t';
                    }
                    return ',';
                }
            }
        }
        return ',';
    }

    /**
     * Configuración de Apache Commons CSV parametrizada con el delimitador detectado.
     */
    private CSVFormat buildCsvFormat(char delimiter) {
        return CSVFormat.DEFAULT.builder()
                .setDelimiter(delimiter)
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreEmptyLines(true)
                .setTrim(true)
                .build();
    }

    /**
     * Construye el perfil completo de una columna: separa valores nulos/vacíos (incluyendo literal "NULL" de SQL)
     * de los válidos, cuenta los valores únicos, infiere el tipo de dato y calcula sus métricas.
     */
    private ColumnProfile buildColumnProfile(String columnName, List<String> values) {
        ColumnProfile profile = new ColumnProfile();
        profile.setColumnName(columnName);

        List<String> validValues = new ArrayList<>();
        int nullCount = 0;

        // Filtramos celdas nulas, en blanco o con el literal "NULL" exportado por SQL Server Management Studio
        for (String value : values) {
            if (isNullOrBlank(value)) {
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

        // Si es numérica calculamos min/max/promedio/mediana;
        // si es DATE agrupamos el Top 5 por día (yyyy-MM-dd) ignorando horas/milisegundos;
        // en caso contrario calculamos el Top 5 de frecuencias exactas.
        if (dataType == ColumnDataType.NUMERIC && !validValues.isEmpty()) {
            calculateNumericStats(profile, validValues);
        } else if (dataType == ColumnDataType.DATE && !validValues.isEmpty()) {
            List<String> normalizedDates = validValues.stream()
                    .map(val -> {
                        LocalDate date = tryParseLocalDate(val);
                        return date != null ? date.toString() : val;
                    })
                    .toList();
            profile.setTopValues(calculateTopValues(normalizedDates, TOP_VALUES_LIMIT));
        } else if (!validValues.isEmpty()) {
            profile.setTopValues(calculateTopValues(validValues, TOP_VALUES_LIMIT));
        } else {
            profile.setTopValues(Map.of());
        }

        return profile;
    }

    /**
     * Determina si una celda debe considerarse nula (null, vacía o texto literal "NULL" de exportaciones SQL).
     */
    private boolean isNullOrBlank(String value) {
        if (value == null || value.isBlank()) {
            return true;
        }
        return "null".equalsIgnoreCase(value.trim());
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
     * Verifica si un texto coincide con alguno de los formatos de fecha o fecha-hora (ISO o SQL Server).
     */
    private boolean isDate(String value) {
        return tryParseLocalDate(value) != null;
    }

    /**
     * Intenta extraer un LocalDate a partir de un string de fecha o fecha-hora (soporta tanto
     * separador ISO 'T' como el espacio ' ' con milisegundos propio de SQL Server).
     * Devuelve null si el texto no es una fecha válida.
     */
    private LocalDate tryParseLocalDate(String value) {
        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                return LocalDate.parse(value, formatter);
            } catch (DateTimeParseException ignored) {
                // Si no coincide con este formato, probamos con el siguiente
            }
        }

        // Soportamos timestamps con hora y milisegundos (ej. "2026-11-16 23:02:00.537" o "2026-11-16T23:02:00")
        String isoCandidate = value.replaceFirst(" ", "T");
        try {
            return LocalDateTime.parse(isoCandidate, DateTimeFormatter.ISO_LOCAL_DATE_TIME).toLocalDate();
        } catch (DateTimeParseException e) {
            return null;
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
