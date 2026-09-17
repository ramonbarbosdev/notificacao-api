package com.notificacao_api.service.github.app;

import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.notificacao_api.model.OrganizacaoConfiguracao;
import com.notificacao_api.service.OrganizacaoGithubAppCredentialsService;
import com.notificacao_api.service.github.GithubIntegracaoSettings;

@Service
public class GithubAppInstallationTokenService {

    private static final Logger log = LoggerFactory.getLogger(GithubAppInstallationTokenService.class);

    private final OrganizacaoGithubAppCredentialsService credentialsService;
    private final GithubAppJwtFactory jwtFactory;
    private final GithubAppInstallationTokenClient tokenClient;
    private final ConcurrentHashMap<CacheKey, CachedToken> cache = new ConcurrentHashMap<>();

    public GithubAppInstallationTokenService(
            OrganizacaoGithubAppCredentialsService credentialsService,
            GithubAppJwtFactory jwtFactory,
            GithubAppInstallationTokenClient tokenClient) {
        this.credentialsService = credentialsService;
        this.jwtFactory = jwtFactory;
        this.tokenClient = tokenClient;
    }

    public String obterToken(
            Long idOrganizacao,
            OrganizacaoConfiguracao config,
            GithubIntegracaoSettings settings,
            long installationId) {
        CacheKey key = new CacheKey(idOrganizacao, installationId);
        Instant agora = Instant.now();
        CachedToken emCache = cache.get(key);
        if (emCache != null && !deveRenovar(agora, emCache.expiresAt, settings.installationTokenSkewSegundos())) {
            return emCache.token;
        }
        return cache.compute(key, (k, atual) -> {
            if (atual != null && !deveRenovar(agora, atual.expiresAt, settings.installationTokenSkewSegundos())) {
                return atual;
            }
            var credenciais = credentialsService.resolverCredenciais(config)
                    .orElseThrow(() -> new IllegalStateException("Credenciais GitHub App ausentes."));
            String jwt = jwtFactory.criarJwt(credenciais);
            GithubAppInstallationTokenClient.InstallationAccessToken novo =
                    tokenClient.solicitar(settings, jwt, installationId);
            log.debug(
                    "GitHub installation token renovado org={} installationId={}",
                    idOrganizacao,
                    installationId);
            return new CachedToken(novo.token(), novo.expiresAt());
        }).token;
    }

    private static boolean deveRenovar(Instant agora, Instant expiresAt, int skewSegundos) {
        return !agora.isBefore(expiresAt.minusSeconds(skewSegundos));
    }

    private record CacheKey(Long idOrganizacao, long installationId) {
        CacheKey {
            Objects.requireNonNull(idOrganizacao);
        }
    }

    private record CachedToken(String token, Instant expiresAt) {
    }
}
