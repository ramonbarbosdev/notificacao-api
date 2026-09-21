package com.notificacao_api.dto.integracao;

import java.util.List;

public record GithubProjectV2StatusOpcoesResponse(
        GithubProjectV2ResumoResponse project,
        boolean sucesso,
        String mensagem,
        List<String> errosGraphql,
        List<GithubProjectV2StatusOpcaoResponse> statusOpcoes) {
}
