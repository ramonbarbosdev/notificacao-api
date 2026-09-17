package com.notificacao_api.service.github.graphql;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.notificacao_api.model.OrganizacaoConfiguracao;
import com.notificacao_api.service.OrganizacaoGithubAppCredentialsService;
import com.notificacao_api.service.OrganizacaoGithubGraphqlTokenService;
import com.notificacao_api.service.github.GithubIntegracaoSettings;
import com.notificacao_api.service.github.app.GithubAppInstallationTokenService;

@Service
public class GithubGraphqlAccessTokenResolver {

    private static final Logger log = LoggerFactory.getLogger(GithubGraphqlAccessTokenResolver.class);

    private final OrganizacaoGithubAppCredentialsService appCredentialsService;
    private final GithubAppInstallationTokenService installationTokenService;
    private final OrganizacaoGithubGraphqlTokenService patTokenService;

    public GithubGraphqlAccessTokenResolver(
            OrganizacaoGithubAppCredentialsService appCredentialsService,
            GithubAppInstallationTokenService installationTokenService,
            OrganizacaoGithubGraphqlTokenService patTokenService) {
        this.appCredentialsService = appCredentialsService;
        this.installationTokenService = installationTokenService;
        this.patTokenService = patTokenService;
    }

    public Optional<String> resolverBearer(
            Long idOrganizacao,
            OrganizacaoConfiguracao config,
            GithubIntegracaoSettings settings,
            Long installationIdWebhook) {
        Long installationId = installationIdWebhook != null
                ? installationIdWebhook
                : (config != null ? config.getNuGithubInstallationId() : null);

        if (appCredentialsService.estaConfigurado(config) && installationId != null && installationId > 0) {
            try {
                String token = installationTokenService.obterToken(
                        idOrganizacao, config, settings, installationId);
                if (StringUtils.hasText(token)) {
                    return Optional.of(token);
                }
            } catch (Exception ex) {
                log.warn(
                        "GitHub App installation token falhou org={} installationId={} motivo={}",
                        idOrganizacao,
                        installationId,
                        ex.getMessage());
            }
        }

        String pat = patTokenService.resolverToken(config);
        return StringUtils.hasText(pat) ? Optional.of(pat.trim()) : Optional.empty();
    }
}
