package com.tabularai.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import com.tabularai.exception.InvalidCsvException;
import com.tabularai.model.AiReport;
import com.tabularai.model.Dataset;
import com.tabularai.model.DatasetStatus;
import com.tabularai.repository.AiReportRepository;
import com.tabularai.repository.DatasetRepository;

@ExtendWith(MockitoExtension.class)
class DatasetOrchestratorServiceTest {

    private StatsEngineService statsEngineService;

    @Mock
    private AiReportService aiReportService;

    @Mock
    private DatasetRepository datasetRepository;

    @Mock
    private AiReportRepository aiReportRepository;

    private DatasetOrchestratorService orchestratorService;

    @BeforeEach
    void setUp() {
        statsEngineService = new StatsEngineService();
        orchestratorService = new DatasetOrchestratorService(
                statsEngineService,
                aiReportService,
                datasetRepository,
                aiReportRepository
        );
    }

    @Test
    @DisplayName("Debe rechazar archivos que no tengan extensión .csv")
    void shouldRejectNonCsvExtension() {
        MockMultipartFile invalidFile = new MockMultipartFile(
                "file",
                "datos.txt",
                "text/plain",
                "col1,col2\n1,2".getBytes(StandardCharsets.UTF_8)
        );

        assertThrows(InvalidCsvException.class, () ->
                orchestratorService.processNewDataset(invalidFile, "Contexto")
        );
    }

    @Test
    @DisplayName("Debe rechazar archivos CSV vacíos o solo con encabezados sin filas de datos")
    void shouldRejectCsvWithNoDataRows() {
        MockMultipartFile headerOnlyFile = new MockMultipartFile(
                "file",
                "vacio.csv",
                "text/csv",
                "col1,col2\n".getBytes(StandardCharsets.UTF_8)
        );

        assertThrows(InvalidCsvException.class, () ->
                orchestratorService.processNewDataset(headerOnlyFile, "Contexto")
        );
    }

    @Test
    @DisplayName("Debe coordinar StatsEngineService, AiReportService y guardar el Dataset completo en cascada")
    void shouldProcessAndSaveValidDataset() {
        String csvContent = "id_venta,producto,precio\n1,Mouse,25.0\n2,Teclado,75.0\n";
        MockMultipartFile validFile = new MockMultipartFile(
                "file",
                "ventas.csv",
                "text/csv",
                csvContent.getBytes(StandardCharsets.UTF_8)
        );

        AiReport mockReport = new AiReport();
        mockReport.setExecutiveSummary("Resumen de prueba");
        mockReport.setFindings(List.of("Hallazgo 1"));

        when(aiReportService.generateReport(any(Dataset.class), eq("Analizar ventas")))
                .thenReturn(mockReport);
        when(datasetRepository.save(any(Dataset.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Dataset result = orchestratorService.processNewDataset(validFile, "Analizar ventas");

        assertNotNull(result);
        assertEquals("ventas.csv", result.getFilename());
        assertEquals(2, result.getRowCount());
        assertEquals(3, result.getColumnCount());
        assertEquals(DatasetStatus.COMPLETED, result.getStatus());
        assertEquals(3, result.getColumnProfiles().size());
        assertEquals(1, result.getAiReports().size());
        verify(datasetRepository).save(any(Dataset.class));
    }
}
