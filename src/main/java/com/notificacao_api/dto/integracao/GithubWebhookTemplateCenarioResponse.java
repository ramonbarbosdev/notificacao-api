package com.notificacao_api.dto.integracao;

public record GithubWebhookTemplateCenarioResponse(
        String id,
        String label,
        String githubEvent,
        String action,
        String descricao) {
}
