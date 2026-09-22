package com.notificacao_api.dto.integracao.github;

public record GithubIntegracaoIssueCommentModuloResponse(
        boolean habilitado,
        boolean implementado,
        int versao) {
}
