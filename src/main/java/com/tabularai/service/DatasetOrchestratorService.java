package com.tabularai.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.tabularai.model.AiReport;
import com.tabularai.model.ColumnProfile;
import com.tabularai.model.Dataset;
import com.tabularai.model.DatasetStatus;
import com.tabularai.repository.AiReportRepository;
import com.tabularai.repository.DatasetRepository;

import jakarta.persistence.EntityNotFoundException;

@Service
public class DatasetOrchestratorService {

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
     * 1. Analiza el CSV con el motor estadístico en Java (StatsEngineService).
     * 2. Construye el Dataset padre y vincula sus ColumnProfiles.
     * 3. Genera el primer diagnóstico semántico con IA (AiReportService).
     * 4. Persiste en cascada el Dataset, sus ColumnProfiles y su AiReport en PostgreSQL.
     */
    public Dataset processNewDataset(MultipartFile file, String userContext) throws IOException {
        byte[] csvBytes = file.getBytes();

        // 1. Cálculo determinista en Java (perfiles por columna y total de filas)
        List<ColumnProfile> profiles = statsEngineService.analyze(new ByteArrayInputStream(csvBytes));
        int rowCount = statsEngineService.countRows(new ByteArrayInputStream(csvBytes));

        // 2. Creación de la entidad padre Dataset
        Dataset dataset = new Dataset();
        dataset.setFilename(file.getOriginalFilename() != null ? file.getOriginalFilename() : "dataset.csv");
        dataset.setFileSizeBytes(file.getSize());
        dataset.setRowCount(rowCount);
        dataset.setColumnCount(profiles.size());
        dataset.setStatus(DatasetStatus.COMPLETED);

        // Vinculamos cada ColumnProfile con el Dataset usando el método helper bidireccional
        for (ColumnProfile profile : profiles) {
            dataset.addColumnProfile(profile);
        }

        // 3. Generación del primer reporte con Spring AI (usando solo los perfiles ya calculados)
        AiReport initialReport = aiReportService.generateReport(dataset, userContext);
        dataset.addAiReport(initialReport);

        // 4. Guardado único en cascada (CascadeType.ALL guarda Dataset + ColumnProfiles + AiReport)
        return datasetRepository.save(dataset);
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
        return datasetRepository.findById(datasetId)
                .orElseThrow(() -> new EntityNotFoundException("Dataset no encontrado con ID: " + datasetId));
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
