package com.notificacao_api.dto.integracao.github;

public record GithubIntegracaoCompartilhadoPatchRequest(
        String dsGithubFraseAtivacaoWhatsapp,
        String dsGithubOrganizationLogin,
        String githubGraphqlToken,
        Long githubAppId,
        Long githubInstallationId,
        String githubAppPrivateKey,
        String githubGraphqlUrl,
        String githubApiBaseUrl,
        Integer githubHttpConnectTimeoutMs,
        Integer githubHttpReadTimeoutMs,
        Integer githubInstallationTokenSkewSegundos) {
}
