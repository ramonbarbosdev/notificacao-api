package com.notificacao_api.service.github.graphql;

import java.util.Arrays;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import com.notificacao_api.dto.integracao.GithubProjectV2ListaResponse;
import com.notificacao_api.dto.integracao.GithubProjectV2ResumoResponse;
import com.notificacao_api.dto.integracao.GithubProjectV2StatusOpcoesResponse;
import com.notificacao_api.dto.integracao.GithubProjectV2VinculoResponse;
import com.notificacao_api.model.OrganizacaoConfiguracao;
import com.notificacao_api.service.OrganizacaoConfiguracaoService;
import com.notificacao_api.service.OrganizacaoGithubIntegracaoSettingsService;
import com.notificacao_api.service.TenantContextService;
import com.notificacao_api.service.github.GithubIntegracaoSettings;

@Service
public class GithubProjectV2IntegracaoService {

    private final TenantContextService tenantContextService;
    private final OrganizacaoConfiguracaoService organizacaoConfiguracaoService;
    private final OrganizacaoGithubIntegracaoSettingsService integracaoSettingsService;
    private final GithubGraphqlAccessTokenResolver accessTokenResolver;
    private final GithubProjectV2CatalogService catalogService;

    public GithubProjectV2IntegracaoService(
            TenantContextService tenantContextService,
            OrganizacaoConfiguracaoService organizacaoConfiguracaoService,
            OrganizacaoGithubIntegracaoSettingsService integracaoSettingsService,
            GithubGraphqlAccessTokenResolver accessTokenResolver,
            GithubProjectV2CatalogService catalogService) {
        this.tenantContextService = tenantContextService;
        this.organizacaoConfiguracaoService = organizacaoConfiguracaoService;
        this.integracaoSettingsService = integracaoSettingsService;
        this.accessTokenResolver = accessTokenResolver;
        this.catalogService = catalogService;
    }

    @Transactional(readOnly = true)
    public GithubProjectV2ListaResponse listarProjects(String orgLoginOverride) {
        OrganizacaoConfiguracao config = configObrigatoria();
        String orgLogin = resolverOrgLogin(config, orgLoginOverride);
        if (!StringUtils.hasText(orgLogin)) {
            return new GithubProjectV2ListaResponse(
                    null, false, "Login da organizacao GitHub nao configurado. Aguarde um webhook ou informe orgLogin.", List.of(), List.of());
        }

        Long idOrganizacao = config.getIdOrganizacao();
        GithubIntegracaoSettings settings = integracaoSettingsService.resolver(config);
        String token = accessTokenResolver
                .resolverBearer(idOrganizacao, config, settings, config.getNuGithubInstallationId())
                .orElse(null);
        if (!StringUtils.hasText(token)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, accessTokenResolver.explicarTokenAusente(config));
        }

        var resultado = catalogService.listarProjects(settings, token, orgLogin);
        return new GithubProjectV2ListaResponse(
                orgLogin,
                resultado.sucesso(),
                resultado.mensagem(),
                resultado.errosGraphql(),
                resultado.dados() != null ? resultado.dados() : List.of());
    }

    @Transactional(readOnly = true)
    public GithubProjectV2StatusOpcoesResponse listarStatusOpcoes(String projectNodeIdOverride) {
        OrganizacaoConfiguracao config = configObrigatoria();
        String projectNodeId = StringUtils.hasText(projectNodeIdOverride)
                ? projectNodeIdOverride.trim()
                : config.getDsGithubProjectV2NodeId();
        if (!StringUtils.hasText(projectNodeId)) {
            return new GithubProjectV2StatusOpcoesResponse(
                    null, false, "Project v2 nao vinculado. Informe projectNodeId ou configure dsGithubProjectV2NodeId.", List.of(), List.of());
        }

        Long idOrganizacao = config.getIdOrganizacao();
        GithubIntegracaoSettings settings = integracaoSettingsService.resolver(config);
        String token = accessTokenResolver
                .resolverBearer(idOrganizacao, config, settings, config.getNuGithubInstallationId())
                .orElse(null);
        if (!StringUtils.hasText(token)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, accessTokenResolver.explicarTokenAusente(config));
        }

        var resultado = catalogService.listarStatusOpcoes(settings, token, projectNodeId);
        if (!resultado.sucesso() || resultado.dados() == null) {
            return new GithubProjectV2StatusOpcoesResponse(
                    null,
                    false,
                    resultado.mensagem(),
                    resultado.errosGraphql(),
                    List.of());
        }
        return new GithubProjectV2StatusOpcoesResponse(
                resultado.dados().project(),
                true,
                null,
                resultado.errosGraphql(),
                resultado.dados().statusOpcoes());
    }

    @Transactional(readOnly = true)
    public GithubProjectV2VinculoResponse obterVinculo() {
        OrganizacaoConfiguracao config = configObrigatoria();
        String orgLogin = config.getDsGithubOrganizationLogin();
        GithubProjectV2ResumoResponse projectResumo = null;
        List<com.notificacao_api.dto.integracao.GithubProjectV2StatusOpcaoResponse> opcoes = List.of();
        String mensagem = null;
        boolean tokenOk = false;

        Long idOrganizacao = config.getIdOrganizacao();
        GithubIntegracaoSettings settings = integracaoSettingsService.resolver(config);
        String token = accessTokenResolver
                .resolverBearer(idOrganizacao, config, settings, config.getNuGithubInstallationId())
                .orElse(null);
        tokenOk = StringUtils.hasText(token);

        if (StringUtils.hasText(config.getDsGithubProjectV2NodeId()) && tokenOk) {
            var resultado = catalogService.listarStatusOpcoes(settings, token, config.getDsGithubProjectV2NodeId());
            if (resultado.sucesso() && resultado.dados() != null) {
                projectResumo = resultado.dados().project();
                opcoes = resultado.dados().statusOpcoes();
            } else {
                mensagem = resultado.mensagem();
                projectResumo = new GithubProjectV2ResumoResponse(
                        config.getDsGithubProjectV2NodeId(),
                        config.getNuGithubProjectV2Number(),
                        null,
                        null);
            }
        } else if (StringUtils.hasText(config.getDsGithubProjectV2NodeId())) {
            projectResumo = new GithubProjectV2ResumoResponse(
                    config.getDsGithubProjectV2NodeId(),
                    config.getNuGithubProjectV2Number(),
                    null,
                    null);
            if (!tokenOk) {
                mensagem = accessTokenResolver.explicarTokenAusente(config);
            }
        }

        return new GithubProjectV2VinculoResponse(
                orgLogin,
                projectResumo,
                opcoes,
                parseListaDisparo(config.getDsGithubStatusDisparo()),
                parseListaDisparo(config.getDsGithubIssueStatusDisparo()),
                parseListaDisparo(config.getDsGithubPrStatusDisparo()),
                tokenOk,
                mensagem);
    }

    private OrganizacaoConfiguracao configObrigatoria() {
        Long idOrganizacao = tenantContextService.idOrganizacaoObrigatoria();
        OrganizacaoConfiguracao config = organizacaoConfiguracaoService.buscarPorOrganizacao(idOrganizacao);
        if (config == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Configuracao da organizacao nao encontrada.");
        }
        return config;
    }

    private static String resolverOrgLogin(OrganizacaoConfiguracao config, String override) {
        if (StringUtils.hasText(override)) {
            return override.trim();
        }
        return config.getDsGithubOrganizationLogin();
    }

    static List<String> parseListaDisparo(String csv) {
        if (!StringUtils.hasText(csv)) {
            return List.of();
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
