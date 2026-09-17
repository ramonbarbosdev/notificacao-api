package com.notificacao_api.service.github.graphql;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.notificacao_api.dto.integracao.GithubGraphqlConsultaResponse;
import com.notificacao_api.dto.integracao.GithubGraphqlConsultaResponse.GithubGraphqlAssigneeConsultaResponse;
import com.notificacao_api.model.OrganizacaoConfiguracao;
import com.notificacao_api.service.OrganizacaoConfiguracaoService;
import com.notificacao_api.service.OrganizacaoGithubAppCredentialsService;
import com.notificacao_api.service.OrganizacaoGithubGraphqlTokenService;
import com.notificacao_api.service.OrganizacaoGithubIntegracaoSettingsService;
import com.notificacao_api.service.TenantContextService;
import com.notificacao_api.service.github.GithubIntegracaoSettings;

@Service
public class GithubGraphqlConsultaService {

    private final TenantContextService tenantContextService;
    private final OrganizacaoConfiguracaoService organizacaoConfiguracaoService;
    private final OrganizacaoGithubIntegracaoSettingsService integracaoSettingsService;
    private final OrganizacaoGithubAppCredentialsService appCredentialsService;
    private final OrganizacaoGithubGraphqlTokenService patTokenService;
    private final GithubGraphqlAccessTokenResolver accessTokenResolver;
    private final GithubGraphqlContentResolver contentResolver;

    public GithubGraphqlConsultaService(
            TenantContextService tenantContextService,
            OrganizacaoConfiguracaoService organizacaoConfiguracaoService,
            OrganizacaoGithubIntegracaoSettingsService integracaoSettingsService,
            OrganizacaoGithubAppCredentialsService appCredentialsService,
            OrganizacaoGithubGraphqlTokenService patTokenService,
            GithubGraphqlAccessTokenResolver accessTokenResolver,
            GithubGraphqlContentResolver contentResolver) {
        this.tenantContextService = tenantContextService;
        this.organizacaoConfiguracaoService = organizacaoConfiguracaoService;
        this.integracaoSettingsService = integracaoSettingsService;
        this.appCredentialsService = appCredentialsService;
        this.patTokenService = patTokenService;
        this.accessTokenResolver = accessTokenResolver;
        this.contentResolver = contentResolver;
    }

    @Transactional(readOnly = true)
    public GithubGraphqlConsultaResponse consultarOrganizacaoAtual(String nodeId, String contentType) {
        Long idOrganizacao = tenantContextService.idOrganizacaoObrigatoria();
        OrganizacaoConfiguracao config = organizacaoConfiguracaoService.buscarPorOrganizacao(idOrganizacao);
        if (config == null) {
            return respostaVazia(idOrganizacao, nodeId, contentType, false, "Configuracao da organizacao nao encontrada.");
        }
        return consultar(idOrganizacao, config, nodeId, contentType);
    }

    @Transactional(readOnly = true)
    public GithubGraphqlConsultaResponse consultar(
            Long idOrganizacao, OrganizacaoConfiguracao config, String nodeId, String contentType) {
        GithubIntegracaoSettings settings = integracaoSettingsService.resolver(config);
        boolean appOk = appCredentialsService.estaConfigurado(config);
        boolean patOk = patTokenService.estaConfigurado(config);
        Long installationId = config != null ? config.getNuGithubInstallationId() : null;
        String token = accessTokenResolver
                .resolverBearer(idOrganizacao, config, settings, installationId)
                .orElse(null);
        boolean tokenDisponivel = token != null;

        GithubGraphqlConsultaResult resultado =
                contentResolver.consultar(idOrganizacao, settings, token, nodeId, contentType);

        if (resultado.detalhes() == null) {
            String mensagem = resultado.mensagemFalha();
            if (!tokenDisponivel && mensagem != null && mensagem.contains("nao configurado")) {
                mensagem = accessTokenResolver.explicarTokenAusente(config);
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

    private static GithubGraphqlConsultaResponse respostaVazia(
            Long idOrganizacao,
            String nodeId,
            String contentType,
            boolean tokenDisponivel,
            String mensagem) {
        return new GithubGraphqlConsultaResponse(
                idOrganizacao,
                null,
                tokenDisponivel,
                false,
                null,
                false,
                nodeId,
                contentType,
                false,
                mensagem,
                List.of(),
                null,
                null,
                null,
                null,
                List.of());
    }
}
