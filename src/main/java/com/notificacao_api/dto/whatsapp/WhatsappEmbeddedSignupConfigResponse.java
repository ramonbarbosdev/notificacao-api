package com.notificacao_api.dto.whatsapp;

public record WhatsappEmbeddedSignupConfigResponse(
        boolean habilitado,
        String appId,
        String configId,
        String webhookUrl) {
}
