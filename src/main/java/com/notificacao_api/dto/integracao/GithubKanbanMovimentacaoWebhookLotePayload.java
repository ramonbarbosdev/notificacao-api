package com.notificacao_api.dto.integracao;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record GithubKanbanMovimentacaoWebhookLotePayload(
        String tipo,
        Long idOrganizacao,
        String periodoInicio,
        String periodoFim,
        Integer total,
        List<GithubKanbanMovimentacaoWebhookPayload> movimentacoes) {
}
