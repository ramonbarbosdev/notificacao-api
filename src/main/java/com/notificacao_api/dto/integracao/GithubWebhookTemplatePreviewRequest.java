package com.notificacao_api.dto.integracao;

import jakarta.validation.constraints.NotBlank;

public record GithubWebhookTemplatePreviewRequest(
        String templateAssunto,
        String templateMensagem,
        @NotBlank String cenarioId) {
}
