package com.notificacao_api.dto.integracao.github;

public record GithubIntegracaoKanbanMovimentacaoWebhookPatchRequest(
        String kanbanMovimentacaoWebhookUrl,
        String kanbanMovimentacaoWebhookAuthorization,
        Boolean kanbanMovimentacaoWebhookHabilitado,
        Boolean githubWhatsappDiretoHabilitado,
        String kanbanMovimentacaoWebhookModoEnvio,
        Integer kanbanMovimentacaoWebhookIntervaloMinutos) {
}
