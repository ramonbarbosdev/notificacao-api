package com.notificacao_api.service.whatsapp;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class WhatsappGatewayWebhookValidator {

    private final String gatewayApiKey;

    public WhatsappGatewayWebhookValidator(
            @Value("${whatsapp.gateway.api-key}") String gatewayApiKey) {
        this.gatewayApiKey = gatewayApiKey;
    }

    public boolean chaveValida(String apiKey) {
        if (apiKey == null || apiKey.isBlank() || gatewayApiKey == null || gatewayApiKey.isBlank()) {
            return false;
        }

        return MessageDigest.isEqual(
                gatewayApiKey.getBytes(StandardCharsets.UTF_8),
                apiKey.getBytes(StandardCharsets.UTF_8));
    }
}
