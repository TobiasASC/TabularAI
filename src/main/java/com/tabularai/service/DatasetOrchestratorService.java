package com.tabularai.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Locale;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.tabularai.exception.InvalidCsvException;
import com.tabularai.model.AiReport;
import com.tabularai.model.ColumnProfile;
import com.tabularai.model.Dataset;
import com.tabularai.model.DatasetStatus;
import com.tabularai.repository.AiReportRepository;
import com.tabularai.repository.DatasetRepository;

import jakarta.persistence.EntityNotFoundException;

@Service
public class DatasetOrchestratorService {

    private static final long MAX_FILE_SIZE_BYTES = 5L * 1024 * 1024; // 5 MB
    private static final int MAX_ROWS = 5000;

    private final StatsEngineService statsEngineService;
    private final AiReportService aiReportService;
    private final DatasetRepository datasetRepository;
    private final AiReportRepository aiReportRepository;

    public DatasetOrchestratorService(
            StatsEngineService statsEngineService,
            AiReportService aiReportService,
            DatasetRepository datasetRepository,
            AiReportRepository aiReportRepository) {
        this.statsEngineService = statsEngineService;
        this.aiReportService = aiReportService;
        this.datasetRepository = datasetRepository;
        this.aiReportRepository = aiReportRepository;
    }

    /**
     * Flujo principal (POST /api/v1/datasets):
     * 1. Valida el archivo CSV (extensión, peso, no vacío y límite de filas).
     * 2. Analiza el CSV con el motor estadístico en Java (StatsEngineService).
     * 3. Construye el Dataset padre y vincula sus ColumnProfiles.
     * 4. Genera el primer diagnóstico semántico con IA (AiReportService).
     * 5. Persiste en cascada el Dataset, sus ColumnProfiles y su AiReport en PostgreSQL.
     */
    public Dataset processNewDataset(MultipartFile file, String userContext) {
        validateCsvFile(file);
        if (userContext != null && userContext.trim().length() > 500) {
            throw new InvalidCsvException("El contexto o pregunta no puede superar los 500 caracteres.");
        }

        byte[] csvBytes;
        List<ColumnProfile> profiles;
        int rowCount;

        try {
            csvBytes = file.getBytes();
            rowCount = statsEngineService.countRows(new ByteArrayInputStream(csvBytes));

            if (rowCount == 0) {
                throw new InvalidCsvException("El archivo CSV no contiene filas de datos.");
            }
            if (rowCount > MAX_ROWS) {
                throw new InvalidCsvException(
                        "El archivo supera el límite máximo permitido de " + MAX_ROWS + " filas (contiene " + rowCount + ")."
                );
            }

            profiles = statsEngineService.analyze(new ByteArrayInputStream(csvBytes));
            if (profiles.isEmpty()) {
                throw new InvalidCsvException("No se detectaron columnas válidas en el encabezado del CSV.");
            }
        } catch (IOException | IllegalArgumentException e) {
            throw new InvalidCsvException("No se pudo leer o parsear el archivo CSV: formato inválido o corrupto.", e);
        }

        // Creación de la entidad padre Dataset
        Dataset dataset = new Dataset();
        dataset.setFilename(file.getOriginalFilename());
        dataset.setFileSizeBytes(file.getSize());
        dataset.setRowCount(rowCount);
        dataset.setColumnCount(profiles.size());
        dataset.setStatus(DatasetStatus.COMPLETED);

        // Vinculamos cada ColumnProfile con el Dataset usando el método helper bidireccional
        for (ColumnProfile profile : profiles) {
            dataset.addColumnProfile(profile);
        }

        // Generación del primer reporte con Spring AI (usando solo los perfiles ya calculados)
        AiReport initialReport = aiReportService.generateReport(dataset, userContext);
        dataset.addAiReport(initialReport);

        // Guardado único en cascada (CascadeType.ALL guarda Dataset + ColumnProfiles + AiReport)
        return datasetRepository.save(dataset);
    }

    /**
     * Valida que el archivo exista, no esté vacío, no supere los 5 MB y tenga extensión .csv.
     */
    private void validateCsvFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidCsvException("El archivo CSV es obligatorio y no puede estar vacío.");
        }

        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new InvalidCsvException("El archivo supera el tamaño máximo permitido de 5 MB.");
        }

        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase(Locale.ROOT).endsWith(".csv")) {
            throw new InvalidCsvException("Formato de archivo no soportado. Solo se permiten archivos con extensión .csv.");
        }
    }

    /**
     * Flujo de re-análisis (POST /api/v1/datasets/{id}/reports):
     * Genera un nuevo reporte de IA con otro enfoque (userContext) reutilizando
     * los ColumnProfiles ya guardados en PostgreSQL, sin volver a subir ni procesar el CSV.
     */
    @Transactional
    public AiReport generateAdditionalReport(Long datasetId, String userContext) {
        Dataset dataset = datasetRepository.findById(datasetId)
                .orElseThrow(() -> new EntityNotFoundException("Dataset no encontrado con ID: " + datasetId));

        // Generamos el nuevo reporte a partir de los perfiles existentes en el dataset
        AiReport newReport = aiReportService.generateReport(dataset, userContext);

        // Sincronizamos la relación y guardamos el nuevo reporte en ai_reports
        dataset.addAiReport(newReport);
        return aiReportRepository.save(newReport);
    }

    /**
     * Lista paginada del historial de datasets ordenados del más reciente al más antiguo.
     */
    @Transactional(readOnly = true)
    public Page<Dataset> getAllDatasets(Pageable pageable) {
        return datasetRepository.findAllByOrderByCreatedAtDesc(pageable);
    }

    /**
     * Obtiene un Dataset por su ID junto con sus perfiles de columna y reportes de IA.
     */
    @Transactional(readOnly = true)
    public Dataset getDatasetById(Long datasetId) {
        Dataset dataset = datasetRepository.findById(datasetId)
                .orElseThrow(() -> new EntityNotFoundException("Dataset no encontrado con ID: " + datasetId));
        // Inicializamos las colecciones LAZY dentro de la transacción antes de mapear al DTO
        dataset.getColumnProfiles().size();
        dataset.getAiReports().size();
        return dataset;
    }

    /**
     * Elimina un Dataset y en cascada (CascadeType.ALL) todos sus ColumnProfiles y AiReports.
     */
    @Transactional
    public void deleteDataset(Long datasetId) {
        Dataset dataset = getDatasetById(datasetId);
        datasetRepository.delete(dataset);
    }
}
