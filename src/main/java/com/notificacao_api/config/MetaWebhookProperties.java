package com.notificacao_api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "meta.webhook")
public record MetaWebhookProperties(
        String verifyToken,
        String appSecret) {
}
