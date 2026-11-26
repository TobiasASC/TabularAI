package com.tabularai.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;

/**
 * Configuración de la documentación interactiva OpenAPI / Swagger UI.
 * Accesible en: http://localhost:8080/swagger-ui/index.html
 */
@Configuration
public class OpenApiConfig {

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
