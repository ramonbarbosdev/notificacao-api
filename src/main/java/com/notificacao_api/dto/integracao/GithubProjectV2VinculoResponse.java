package com.notificacao_api.dto.integracao;

import java.util.List;

public record GithubProjectV2VinculoResponse(
        String organizationLogin,
        GithubProjectV2ResumoResponse project,
        List<GithubProjectV2StatusOpcaoResponse> statusOpcoes,
        List<String> disparoGeral,
        List<String> disparoIssue,
        List<String> disparoPr,
        boolean graphqlTokenDisponivel,
        String mensagem) {
}
