package com.notificacao_api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "whatsapp.meta")
public record WhatsappMetaProperties(
        String graphBaseUrl,
        int connectTimeoutMs,
        int readTimeoutMs,
        int maxRetriesTransient) {
}
