package com.notificacao_api.dto.integracao;

import java.util.List;

public record GithubProjectV2ListaResponse(
        String organizationLogin,
        boolean sucesso,
        String mensagem,
        List<String> errosGraphql,
        List<GithubProjectV2ResumoResponse> projects) {
}
