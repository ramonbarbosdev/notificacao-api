package com.notificacao_api.dto.integracao.github;

public record GithubIntegracaoModuloStatusResponse(
        String codigo,
        String titulo,
        boolean habilitado,
        boolean implementado) {
}
