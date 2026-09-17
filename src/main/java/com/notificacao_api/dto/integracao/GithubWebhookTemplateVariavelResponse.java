package com.notificacao_api.dto.integracao;

public record GithubWebhookTemplateVariavelResponse(
        String chave,
        String titulo,
        String descricao,
        String origemPayload,
        String exemplo,
        String dicaUso) {
}
