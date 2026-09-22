package com.notificacao_api.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.notificacao_api.model.github.OrganizacaoGithubIntegracao;
import com.notificacao_api.service.github.GithubIntegracaoDefaults;
import com.notificacao_api.service.github.GithubIntegracaoSettings;

class OrganizacaoGithubIntegracaoSettingsServiceTest {

    private final OrganizacaoGithubIntegracaoSettingsService service = new OrganizacaoGithubIntegracaoSettingsService();

    @Test
    void resolverComColunasNullUsaDefaults() {
        GithubIntegracaoSettings settings = service.resolver(new OrganizacaoGithubIntegracao());

        assertEquals(GithubIntegracaoDefaults.GRAPHQL_URL, settings.graphqlUrl());
        assertEquals(GithubIntegracaoDefaults.API_BASE_URL, settings.apiBaseUrl());
        assertEquals(GithubIntegracaoDefaults.HTTP_CONNECT_TIMEOUT_MS, settings.httpConnectTimeoutMs());
        assertEquals(GithubIntegracaoDefaults.HTTP_READ_TIMEOUT_MS, settings.httpReadTimeoutMs());
        assertEquals(GithubIntegracaoDefaults.INSTALLATION_TOKEN_SKEW_SEGUNDOS, settings.installationTokenSkewSegundos());
    }

    @Test
    void resolverComValoresCustomizados() {
        OrganizacaoGithubIntegracao config = new OrganizacaoGithubIntegracao();
        config.setDsGithubGraphqlUrl("https://github.example.com/graphql");
        config.setDsGithubApiBaseUrl("https://github.example.com");
        config.setNuGithubHttpConnectTimeoutMs(5000);
        config.setNuGithubHttpReadTimeoutMs(15000);
        config.setNuGithubInstallationTokenSkewSegundos(120);

        var settings = service.resolver(config);

        assertEquals("https://github.example.com/graphql", settings.graphqlUrl());
        assertEquals("https://github.example.com", settings.apiBaseUrl());
        assertEquals(5000, settings.httpConnectTimeoutMs());
        assertEquals(15000, settings.httpReadTimeoutMs());
        assertEquals(120, settings.installationTokenSkewSegundos());
    }
}
