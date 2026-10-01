package com.notificacao_api.dto.integracao;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record GithubKanbanMovimentacaoWebhookPayload(
        String repo,
        String tipo,
        Integer numero,
        String titulo,
        String url,
        String de,
        String para,
        String movidoPor,
        GithubKanbanWebhookPessoa movidoPorDetalhe,
        List<String> responsaveis,
        List<GithubKanbanWebhookPessoa> responsaveisDetalhe,
        List<String> labels,
        String prioridade,
        String targetDate,
        String ocorridoEm) {
}
