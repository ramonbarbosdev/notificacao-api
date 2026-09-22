package com.notificacao_api.service.github.graphql;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.notificacao_api.dto.integracao.GithubGraphqlConsultaResponse;
import com.notificacao_api.dto.integracao.GithubGraphqlConsultaResponse.GithubGraphqlAssigneeConsultaResponse;
import com.notificacao_api.model.github.OrganizacaoGithubIntegracao;
import com.notificacao_api.service.OrganizacaoGithubAppCredentialsService;
import com.notificacao_api.service.OrganizacaoGithubGraphqlTokenService;
import com.notificacao_api.service.OrganizacaoGithubIntegracaoSettingsService;
import com.notificacao_api.service.TenantContextService;
import com.notificacao_api.service.github.GithubIntegracaoConfigService;
import com.notificacao_api.service.github.GithubIntegracaoSettings;

@Service
public class GithubGraphqlConsultaService {

    private final TenantContextService tenantContextService;
    private final GithubIntegracaoConfigService githubIntegracaoConfigService;
    private final OrganizacaoGithubIntegracaoSettingsService integracaoSettingsService;
    private final OrganizacaoGithubAppCredentialsService appCredentialsService;
    private final OrganizacaoGithubGraphqlTokenService patTokenService;
    private final GithubGraphqlAccessTokenResolver accessTokenResolver;
    private final GithubGraphqlContentResolver contentResolver;

    public GithubGraphqlConsultaService(
            TenantContextService tenantContextService,
            GithubIntegracaoConfigService githubIntegracaoConfigService,
            OrganizacaoGithubIntegracaoSettingsService integracaoSettingsService,
            OrganizacaoGithubAppCredentialsService appCredentialsService,
            OrganizacaoGithubGraphqlTokenService patTokenService,
            GithubGraphqlAccessTokenResolver accessTokenResolver,
            GithubGraphqlContentResolver contentResolver) {
        this.tenantContextService = tenantContextService;
        this.githubIntegracaoConfigService = githubIntegracaoConfigService;
        this.integracaoSettingsService = integracaoSettingsService;
        this.appCredentialsService = appCredentialsService;
        this.patTokenService = patTokenService;
        this.accessTokenResolver = accessTokenResolver;
        this.contentResolver = contentResolver;
    }

    public GithubGraphqlConsultaResponse consultarOrganizacaoAtual(String nodeId, String contentType) {
        Long idOrganizacao = tenantContextService.idOrganizacaoObrigatoria();
        OrganizacaoGithubIntegracao integracao = githubIntegracaoConfigService.obterIntegracao(idOrganizacao);
        return consultar(idOrganizacao, integracao, nodeId, contentType);
    }

    public GithubGraphqlConsultaResponse consultar(
            Long idOrganizacao, OrganizacaoGithubIntegracao integracao, String nodeId, String contentType) {
        GithubIntegracaoSettings settings = integracaoSettingsService.resolver(integracao);
        boolean appOk = appCredentialsService.estaConfigurado(integracao);
        boolean patOk = patTokenService.estaConfigurado(integracao);
        Long installationId = integracao != null ? integracao.getNuGithubInstallationId() : null;
        String token = accessTokenResolver
                .resolverBearer(idOrganizacao, integracao, settings, installationId)
                .orElse(null);
        boolean tokenDisponivel = token != null;

        GithubGraphqlConsultaResult resultado =
                contentResolver.consultar(idOrganizacao, settings, token, nodeId, contentType);

        if (resultado.detalhes() == null) {
            String mensagem = resultado.mensagemFalha();
            if (!tokenDisponivel && mensagem != null && mensagem.contains("nao configurado")) {
                mensagem = accessTokenResolver.explicarTokenAusente(integracao);
            }
            return new GithubGraphqlConsultaResponse(
                    idOrganizacao,
                    settings.graphqlUrl(),
                    tokenDisponivel,
                    appOk,
                    installationId,
                    patOk,
                    nodeId,
                    contentType,
                    resultado.sucesso(),
                    mensagem,
                    resultado.errosGraphql(),
                    null,
                    null,
                    null,
                    null,
                    List.of());
        }

        GithubProjectV2ContentDetalhes detalhes = resultado.detalhes();
        List<GithubGraphqlAssigneeConsultaResponse> assignees = new ArrayList<>();
        for (GithubGraphqlAssignee assignee : detalhes.assignees()) {
            assignees.add(new GithubGraphqlAssigneeConsultaResponse(assignee.login(), assignee.name()));
        }

        return new GithubGraphqlConsultaResponse(
                idOrganizacao,
                settings.graphqlUrl(),
                tokenDisponivel,
                appOk,
                installationId,
                patOk,
                nodeId,
                contentType,
                resultado.sucesso(),
                resultado.mensagemFalha(),
                resultado.errosGraphql(),
                detalhes.contentTypename(),
                detalhes.titulo(),
                detalhes.url(),
                detalhes.numero(),
                assignees);
    }
}
