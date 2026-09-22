package com.notificacao_api.dto.integracao.github;

import java.util.List;

public record GithubIntegracaoHubResponse(
        Long idOrganizacao,
        boolean featureHabilitada,
        List<GithubIntegracaoModuloStatusResponse> modulos) {
}
