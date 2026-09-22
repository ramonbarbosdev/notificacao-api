package com.notificacao_api.service.github.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.notificacao_api.model.github.OrganizacaoGithubIntegracao;
import com.notificacao_api.service.OrganizacaoGithubAppCredentialsService;
import com.notificacao_api.service.github.GithubIntegracaoDefaults;
import com.notificacao_api.service.github.GithubIntegracaoSettings;

@ExtendWith(MockitoExtension.class)
class GithubAppInstallationTokenServiceTest {

    @Mock
    private OrganizacaoGithubAppCredentialsService credentialsService;
    @Mock
    private GithubAppJwtFactory jwtFactory;
    @Mock
    private GithubAppInstallationTokenClient tokenClient;

    private GithubAppInstallationTokenService service;

    private final GithubIntegracaoSettings settings = new GithubIntegracaoSettings(
            GithubIntegracaoDefaults.GRAPHQL_URL,
            GithubIntegracaoDefaults.API_BASE_URL,
            1000,
            1000,
            600);

    @BeforeEach
    void setUp() {
        service = new GithubAppInstallationTokenService(credentialsService, jwtFactory, tokenClient);
    }

    @Test
    void cacheEvitaSegundaChamadaHttpDentroDoSkew() {
        OrganizacaoGithubIntegracao config = new OrganizacaoGithubIntegracao();
        config.setNuGithubAppId(1L);
        when(credentialsService.resolverCredenciais(config))
                .thenReturn(Optional.of(new GithubAppCredentials(1L, "pem")));
        when(jwtFactory.criarJwt(any())).thenReturn("jwt");
        Instant expires = Instant.now().plusSeconds(3600);
        when(tokenClient.solicitar(eq(settings), eq("jwt"), eq(99L)))
                .thenReturn(new GithubAppInstallationTokenClient.InstallationAccessToken("ghs_abc", expires));

        assertEquals("ghs_abc", service.obterToken(1L, config, settings, 99L));
        assertEquals("ghs_abc", service.obterToken(1L, config, settings, 99L));

        verify(tokenClient, times(1)).solicitar(eq(settings), eq("jwt"), eq(99L));
    }
}
