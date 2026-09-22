package com.notificacao_api.dto.integracao.github;

public record GithubIntegracaoCompartilhadoResponse(
        Long idOrganizacao,
        String dsGithubFraseAtivacaoWhatsapp,
        String dsGithubOrganizationLogin,
        boolean githubGraphqlTokenConfigurado,
        Long githubAppId,
        Long githubInstallationId,
        boolean githubAppPrivateKeyConfigurado,
        String githubGraphqlUrl,
        String githubApiBaseUrl,
        Integer githubHttpConnectTimeoutMs,
        Integer githubHttpReadTimeoutMs,
        Integer githubInstallationTokenSkewSegundos) {
}
