package com.tabularai.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.tabularai.dto.AiReportDto;
import com.tabularai.dto.CreateReportRequest;
import com.tabularai.dto.DatasetResponse;
import com.tabularai.dto.DatasetSummaryDto;
import com.tabularai.model.AiReport;
import com.tabularai.model.Dataset;
import com.tabularai.service.DatasetOrchestratorService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/datasets")
@Tag(name = "Datasets", description = "Endpoints para ingesta de archivos CSV, perfilado estadístico y generación de reportes con IA")
public class DatasetController {

    private final DatasetOrchestratorService orchestratorService;

    public DatasetController(DatasetOrchestratorService orchestratorService) {
        this.orchestratorService = orchestratorService;
    }

    /**
     * 1. POST /api/v1/datasets
     * Recibe un archivo CSV y un contexto opcional, calcula las estadísticas por columna,
     * genera el primer reporte con IA y guarda todo en PostgreSQL.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Subir y analizar un nuevo dataset CSV",
            description = "Procesa el archivo CSV en memoria, calcula los perfiles estadísticos exactos por columna, "
                    + "genera el primer diagnóstico con IA y persiste los resultados."
    )
    public ResponseEntity<DatasetResponse> uploadDataset(
            @Parameter(description = "Archivo CSV a analizar (máx. 5 MB y 5.000 filas)", required = true)
            @RequestPart("file") MultipartFile file,
            @Parameter(description = "Enfoque o pregunta opcional para guiar el análisis de la IA")
            @RequestParam(value = "context", required = false) String context) {

        Dataset savedDataset = orchestratorService.processNewDataset(file, context);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(DatasetResponse.fromEntity(savedDataset));
    }

    /**
     * 2. GET /api/v1/datasets
     * Devuelve el historial paginado de datasets cargados (solo metadatos).
     */
    @GetMapping
    @Operation(
            summary = "Listar historial de datasets (paginado)",
            description = "Devuelve una lista paginada con los metadatos de los datasets cargados, ordenados del más reciente al más antiguo."
    )
    public ResponseEntity<Page<DatasetSummaryDto>> getAllDatasets(
            @Parameter(description = "Número de página (comienza en 0)")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Cantidad de registros por página (1 a 100)")
            @RequestParam(defaultValue = "10") int size) {

        int safePage = Math.max(0, page);
        int safeSize = Math.clamp(size, 1, 100);

        Page<DatasetSummaryDto> summaries = orchestratorService
                .getAllDatasets(PageRequest.of(safePage, safeSize))
                .map(DatasetSummaryDto::fromEntity);

        return ResponseEntity.ok(summaries);
    }

    /**
     * 3. GET /api/v1/datasets/{id}
     * Devuelve un dataset completo con todos sus perfiles estadísticos y reportes de IA.
     */
    @GetMapping("/{id}")
    @Operation(
            summary = "Obtener detalle completo de un dataset",
            description = "Devuelve los metadatos del dataset junto con todos sus perfiles de columna (columnProfiles) y reportes de IA (aiReports)."
    )
    public ResponseEntity<DatasetResponse> getDatasetById(
            @Parameter(description = "ID del dataset")
            @PathVariable Long id) {

        Dataset dataset = orchestratorService.getDatasetById(id);
        return ResponseEntity.ok(DatasetResponse.fromEntity(dataset));
    }

    /**
     * 4. POST /api/v1/datasets/{id}/reports
     * Genera un nuevo reporte de IA con un nuevo enfoque sobre un dataset ya existente (sin resubir el CSV).
     */
    @PostMapping("/{id}/reports")
    @Operation(
            summary = "Generar un nuevo reporte de IA sobre un dataset existente",
            description = "Reutiliza los perfiles estadísticos ya guardados en PostgreSQL para generar un nuevo diagnóstico con otro enfoque (sin reprocesar el CSV)."
    )
    public ResponseEntity<AiReportDto> generateAdditionalReport(
            @Parameter(description = "ID del dataset existente")
            @PathVariable Long id,
            @Valid @RequestBody CreateReportRequest request) {

        AiReport newReport = orchestratorService.generateAdditionalReport(id, request.context());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(AiReportDto.fromEntity(newReport));
    }

    /**
     * 5. DELETE /api/v1/datasets/{id}
     * Elimina un dataset y en cascada todos sus perfiles y reportes asociados.
     */
    @DeleteMapping("/{id}")
    @Operation(
            summary = "Eliminar un dataset",
            description = "Elimina el dataset indicado y, en cascada (CascadeType.ALL), todos sus perfiles de columna y reportes de IA."
    )
    public ResponseEntity<Void> deleteDataset(
            @Parameter(description = "ID del dataset a eliminar")
            @PathVariable Long id) {

        orchestratorService.deleteDataset(id);
        return ResponseEntity.noContent().build();
    }
}
