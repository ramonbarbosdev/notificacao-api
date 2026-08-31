package com.notificacao_api.dto.integracao;

public record WhatsappWebhookInboundResponse(
        String url,
        Boolean habilitado,
        boolean secretConfigurado) {
}
