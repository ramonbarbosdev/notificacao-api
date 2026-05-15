package com.notificacao_api.config;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.beans.factory.annotation.Value;

import java.util.List;
import io.swagger.v3.oas.models.servers.Server;

@Configuration
@Profile("prod")
public class SwaggerProdConfiguracao {

        @Bean
        public OpenAPI openApiProd(@Value("${app.swagger.server-url}") String serverUrl) {
                final String securitySchemeName = "bearerAuth";
                return new OpenAPI()
                                .info(new Info()
                                                .title("API Notificações")
                                                .version("v1")
                                                .description("API pública"))
                                .components(new Components()
                                                .addSecuritySchemes(securitySchemeName,
                                                                new SecurityScheme()
                                                                                .name(securitySchemeName)
                                                                                .type(SecurityScheme.Type.HTTP)
                                                                                .scheme("bearer")
                                                                                .bearerFormat("JWT")))
                                .addSecurityItem(new SecurityRequirement().addList(securitySchemeName))
                                .servers(List.of(
                                                new Server().url(serverUrl)
                                                                .description("Servidor de producao")));
        }
}
