package com.notificacao_api.service.github;

public record GithubIntegracaoSettings(
        String graphqlUrl,
        String apiBaseUrl,
        int httpConnectTimeoutMs,
        int httpReadTimeoutMs,
        int installationTokenSkewSegundos) {
}
