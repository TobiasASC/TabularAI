package com.tabularai.service;

import java.util.List;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.tabularai.dto.AiInsightsDto;
import com.tabularai.model.AiReport;
import com.tabularai.model.ColumnDataType;
import com.tabularai.model.ColumnProfile;
import com.tabularai.model.Dataset;

@Service
public class AiReportService {

    private static final String DEFAULT_USER_CONTEXT =
            "Análisis general exploratorio, detección de anomalías y evaluación de calidad de datos.";

    // Instrucciones de comportamiento general para el modelo (System Prompt)
    private static final String SYSTEM_PROMPT = """
            Eres un Analista de Datos Senior experto en estadística descriptiva y calidad de datos.
            Tu tarea es analizar el perfil estadístico de las columnas de un dataset tabular y generar un diagnóstico claro, preciso y accionable en español.
            
            Reglas estrictas:
            1. Basa tus conclusiones ÚNICAMENTE en las métricas matemáticas proporcionadas (no inventes números ni asumas datos que no están en el perfil).
            2. En columnas numéricas (NUMERIC), utiliza el Total (Suma) para informar montos o cantidades acumuladas, y compara el promedio (mean) con la mediana (median), la desviación estándar (stdDev) y los extremos (min/max) para detectar volatilidad, sesgos o valores atípicos (outliers).
            3. En columnas identificadoras (IDENTIFIER, como id_venta, id_persona, id_medio_pago), NUNCA hables de promedios; si 'Únicos' es igual al total de filas es una clave primaria, y si tiene valores repetidos en 'Top Frecuencias' son claves foráneas que indican qué entidad (ej. qué cliente o medio de pago) se repite más veces.
            4. Señala problemas de calidad de datos cuando existan valores nulos/vacíos (nullCount > 0) o baja variedad de datos.
            5. Adapta tu análisis y tus hallazgos al enfoque o pregunta específica solicitada por el usuario (Contexto del usuario).
            6. Responde siempre en español.
            """;

    // Plantilla con variables dinámicas {placeholders} que Spring AI reemplazará en cada consulta (User Prompt)
    private static final String USER_PROMPT_TEMPLATE = """
            Analiza el siguiente dataset:
            - Archivo: {filename}
            - Total de filas: {rowCount}
            - Total de columnas: {columnCount}
            - Enfoque solicitado por el usuario: {userContext}
            
            Perfiles estadísticos por columna (calculados previamente):
            {profilesSummary}
            """;

    private final ChatClient chatClient;
    private final String modelName;

    /**
     * Spring AI inyecta automáticamente un ChatClient.Builder preconfigurado con las propiedades
     * de Groq definidas en application.properties.
     */
    public AiReportService(
            ChatClient.Builder chatClientBuilder,
            @Value("${spring.ai.openai.chat.options.model:openai/gpt-oss-120b}") String modelName) {
        this.chatClient = chatClientBuilder.build();
        this.modelName = modelName;
    }

    /**
     * Envía los perfiles estadísticos del Dataset y el contexto del usuario al LLM,
     * obtiene la respuesta estructurada en un AiInsightsDto y construye la entidad AiReport.
     */
    public AiReport generateReport(Dataset dataset, String userContext) {
        // 1. Si el usuario no envió un contexto específico, usamos uno por defecto
        String effectiveContext = (userContext == null || userContext.isBlank())
                ? DEFAULT_USER_CONTEXT
                : userContext.trim();

        // 2. Convertimos la lista de ColumnProfile a un resumen textual limpio para el prompt (sin enviar filas crudas del CSV)
        String profilesSummary = formatProfilesForPrompt(dataset.getColumnProfiles());

        // 3. Llamamos al LLM usando la API fluida de ChatClient y pedimos un Structured Output (AiInsightsDto)
        AiInsightsDto insights = chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(userSpec -> userSpec
                        .text(USER_PROMPT_TEMPLATE)
                        .param("filename", dataset.getFilename())
                        .param("rowCount", dataset.getRowCount())
                        .param("columnCount", dataset.getColumnCount())
                        .param("userContext", effectiveContext)
                        .param("profilesSummary", profilesSummary)
                )
                .call()
                .entity(AiInsightsDto.class);

        // 4. Mapeamos la respuesta del DTO inmutable a nuestra entidad JPA AiReport
        AiReport report = new AiReport();
        report.setDataset(dataset);
        report.setUserContext(effectiveContext);
        report.setModelUsed(this.modelName);

        if (insights != null) {
            report.setExecutiveSummary(insights.executiveSummary());
            report.setFindings(insights.findings() != null ? insights.findings() : List.of());
        } else {
            report.setExecutiveSummary("No se pudo generar el resumen ejecutivo.");
            report.setFindings(List.of());
        }

        return report;
    }

    /**
     * Transforma la lista de ColumnProfile en un resumen compacto para el LLM.
     * Evita enviar campos irrelevantes (como min/max nulos en columnas de texto) para ahorrar tokens.
     */
    private String formatProfilesForPrompt(List<ColumnProfile> profiles) {
        if (profiles == null || profiles.isEmpty()) {
            return "Sin columnas registradas.";
        }

        StringBuilder sb = new StringBuilder();
        for (ColumnProfile profile : profiles) {
            sb.append("- Columna '").append(profile.getColumnName()).append("'")
              .append(" [Tipo: ").append(profile.getDataType()).append("]")
              .append(" | Nulos: ").append(profile.getNullCount())
              .append(" | Únicos: ").append(profile.getUniqueCount());

            if (profile.getDataType() == ColumnDataType.NUMERIC) {
                sb.append(" | Total (Suma): ").append(profile.getSumVal())
                  .append(" | Min: ").append(profile.getMinVal())
                  .append(" | Max: ").append(profile.getMaxVal())
                  .append(" | Promedio (Mean): ").append(profile.getMeanVal())
                  .append(" | Mediana (Median): ").append(profile.getMedianVal())
                  .append(" | Desv. Estándar (StdDev): ").append(profile.getStdDevVal());
            } else if (profile.getDataType() == ColumnDataType.IDENTIFIER
                    && (profile.getTopValues() == null || profile.getTopValues().isEmpty())) {
                sb.append(" | Rol: Clave Primaria (100% valores únicos)");
            } else if (profile.getTopValues() != null && !profile.getTopValues().isEmpty()) {
                sb.append(" | Top Frecuencias: ").append(profile.getTopValues());
            }

            sb.append("\n");
        }

        return sb.toString();
    }
}
