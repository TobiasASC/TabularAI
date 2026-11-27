package com.tabularai.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;

/**
 * Configuración de la documentación interactiva OpenAPI / Swagger UI.
 * Accesible en: http://localhost:8080/swagger-ui/index.html
 */
@Configuration
public class OpenApiConfig {

    private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;

    public OpenApiConfig(ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        this.jdbcTemplateProvider = jdbcTemplateProvider;
    }

    /**
     * Al iniciar la aplicación actualiza la restricción CHECK de PostgreSQL en column_profiles
     * para permitir el nuevo tipo de dato IDENTIFIER en bases de datos ya existentes.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void updateDatabaseEnumConstraint() {
        jdbcTemplateProvider.ifAvailable(jdbc -> {
            try {
                jdbc.execute("ALTER TABLE IF EXISTS column_profiles DROP CONSTRAINT IF EXISTS column_profiles_data_type_check");
                // Rellenamos sum_val en datasets numéricos antiguos que fueron cargados antes de existir esta columna
                jdbc.execute("""
                        UPDATE column_profiles cp
                        SET sum_val = ROUND(CAST(cp.mean_val * (d.row_count - cp.null_count) AS numeric), 2)
                        FROM datasets d
                        WHERE cp.dataset_id = d.dataset_id
                          AND cp.data_type = 'NUMERIC'
                          AND cp.sum_val IS NULL
                          AND cp.mean_val IS NOT NULL
                        """);
            } catch (Exception ignored) {
                // Si la tabla aún no existe o no tiene la restricción, continuamos normalmente
            }
        });
    }

    @Bean
    public OpenAPI tabularAiOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("TabularAI — API de Análisis Inteligente de Datasets")
                        .version("1.0.0")
                        .description("API REST que combina cálculo estadístico determinista en Java (Apache Commons CSV) "
                                + "con diagnósticos semánticos estructurados mediante Spring AI y Groq.")
                        .contact(new Contact()
                                .name("TabularAI")
                        )
                );
    }
}
