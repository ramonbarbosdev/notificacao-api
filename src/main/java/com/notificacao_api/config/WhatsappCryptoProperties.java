package com.notificacao_api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "whatsapp.crypto")
public record WhatsappCryptoProperties(String encryptionKey) {
}
