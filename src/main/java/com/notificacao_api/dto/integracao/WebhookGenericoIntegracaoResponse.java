package com.notificacao_api.dto.integracao;

public record WebhookGenericoIntegracaoResponse(
        boolean featureHabilitada,
        String urlWebhook,
        String autenticacao,
        String corpoJsonExemplo,
        String observacoes) {
}
