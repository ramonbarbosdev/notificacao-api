package com.notificacao_api.dto.integracao;

public record GithubWebhookIntegracaoResponse(
        boolean featureHabilitada,
        String webhookUrlTemplate,
        String instrucaoWebhookSecret,
        String instrucaoResponsaveis,
        String fraseAtivacaoWhatsapp,
        String linkWhatsappAtivacao,
        boolean whatsappOrigemConectado) {
}
