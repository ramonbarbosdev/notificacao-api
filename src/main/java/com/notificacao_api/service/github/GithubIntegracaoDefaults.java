package com.notificacao_api.service.github;

public final class GithubIntegracaoDefaults {

    public static final String GRAPHQL_URL = "https://api.github.com/graphql";
    public static final String API_BASE_URL = "https://api.github.com";
    public static final int HTTP_CONNECT_TIMEOUT_MS = 10_000;
    public static final int HTTP_READ_TIMEOUT_MS = 30_000;
    public static final int INSTALLATION_TOKEN_SKEW_SEGUNDOS = 300;

    private GithubIntegracaoDefaults() {
    }
}
