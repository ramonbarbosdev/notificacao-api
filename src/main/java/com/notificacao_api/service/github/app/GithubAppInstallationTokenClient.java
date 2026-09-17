package com.notificacao_api.service.github.app;

import java.time.Duration;
import java.time.Instant;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.notificacao_api.service.github.GithubIntegracaoSettings;

@Component
public class GithubAppInstallationTokenClient {

    private final RestClient.Builder restClientBuilder;

    public GithubAppInstallationTokenClient(RestClient.Builder restClientBuilder) {
        this.restClientBuilder = restClientBuilder;
    }

    public InstallationAccessToken solicitar(
            GithubIntegracaoSettings settings,
            String appJwt,
            long installationId) {
        String baseUrl = settings.apiBaseUrl().replaceAll("/+$", "");
        var httpFactory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        httpFactory.setConnectTimeout(Duration.ofMillis(settings.httpConnectTimeoutMs()));
        httpFactory.setReadTimeout(Duration.ofMillis(settings.httpReadTimeoutMs()));

        RestClient client = restClientBuilder
                .baseUrl(baseUrl)
                .requestFactory(httpFactory)
                .build();

        JsonNode resposta = client.post()
                .uri("/app/installations/{installationId}/access_tokens", installationId)
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + appJwt)
                .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .retrieve()
                .body(JsonNode.class);

        if (resposta == null || resposta.isNull()) {
            throw new IllegalStateException("Resposta vazia ao solicitar installation access token.");
        }
        String token = texto(resposta, "token");
        String expiresAt = texto(resposta, "expires_at");
        if (!StringUtils.hasText(token) || !StringUtils.hasText(expiresAt)) {
            throw new IllegalStateException("Installation access token incompleto na resposta GitHub.");
        }
        return new InstallationAccessToken(token.trim(), Instant.parse(expiresAt.trim()));
    }

    private static String texto(JsonNode node, String field) {
        JsonNode valor = node.get(field);
        if (valor == null || valor.isNull()) {
            return null;
        }
        return valor.asText(null);
    }

    public record InstallationAccessToken(String token, Instant expiresAt) {
    }
}
