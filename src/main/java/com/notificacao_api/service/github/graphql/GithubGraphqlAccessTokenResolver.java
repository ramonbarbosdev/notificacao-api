package com.notificacao_api.service.github.graphql;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.notificacao_api.model.github.OrganizacaoGithubIntegracao;
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
            OrganizacaoGithubIntegracao integracao,
            GithubIntegracaoSettings settings,
            Long installationIdWebhook) {
        Long installationId = installationIdWebhook != null
                ? installationIdWebhook
                : (integracao != null ? integracao.getNuGithubInstallationId() : null);

        if (appCredentialsService.estaConfigurado(integracao) && installationId != null && installationId > 0) {
            try {
                String token = installationTokenService.obterToken(
                        idOrganizacao, integracao, settings, installationId);
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

        String pat = patTokenService.resolverToken(integracao);
        return StringUtils.hasText(pat) ? Optional.of(pat.trim()) : Optional.empty();
    }

    public String explicarTokenAusente(OrganizacaoGithubIntegracao integracao) {
        if (integracao == null) {
            return "Integracao GitHub da organizacao nao encontrada.";
        }
        boolean appOk = appCredentialsService.estaConfigurado(integracao);
        boolean patOk = patTokenService.estaConfigurado(integracao);
        Long installationId = integracao.getNuGithubInstallationId();

        if (!appOk && !patOk) {
            return "Configure GitHub App (App ID + chave PEM + Installation ID) na aba Conexao, "
                    + "ou um PAT GraphQL salvo para esta organizacao.";
        }
        if (!appOk && patOk) {
            return "PAT GraphQL esta salvo mas nao foi possivel obter o token (verifique criptografia/valor).";
        }
        if (appOk && (installationId == null || installationId <= 0) && !patOk) {
            return "GitHub App sem Installation ID. Preencha em Conexao (ou envie um webhook da instalacao "
                    + "para preencher automaticamente) e salve.";
        }
        if (appOk && installationId != null && installationId > 0 && !patOk) {
            return "GitHub App e Installation ID estao salvos, mas a API nao obteve token de instalacao "
                    + "(App ID, PEM ou ID incorretos, ou App sem permissao no repositorio). "
                    + "Confira os logs da API ou configure um PAT como fallback.";
        }
        return "Token GitHub App ou PAT nao disponivel para esta organizacao.";
    }
}
