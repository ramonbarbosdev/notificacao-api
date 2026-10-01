package com.notificacao_api.dto.integracao.github;

public record GithubIntegracaoKanbanMovimentacaoWebhookResponse(
        Long idOrganizacao,
        String kanbanMovimentacaoWebhookUrl,
        boolean kanbanMovimentacaoWebhookHabilitado,
        boolean kanbanMovimentacaoWebhookAuthorizationConfigurado,
        boolean githubWhatsappDiretoHabilitado) {
}
