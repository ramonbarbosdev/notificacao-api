package com.notificacao_api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "asaas")
public record AsaasProperties(
        String apiKey,
        String baseUrl,
        String webhookAccessToken) {
}
