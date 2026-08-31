package com.notificacao_api.dto.integracao;

import jakarta.validation.constraints.NotNull;

public record WhatsappWebhookInboundRequest(
        String url,
        String secret,
        @NotNull Boolean habilitado) {
}
