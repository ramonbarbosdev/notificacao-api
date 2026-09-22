package com.notificacao_api.service.github;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.notificacao_api.dto.configuracao.GithubTemplatePorCenarioDto;
import com.notificacao_api.dto.integracao.github.GithubIntegracaoCompartilhadoPatchRequest;
import com.notificacao_api.dto.integracao.github.GithubIntegracaoCompartilhadoResponse;
import com.notificacao_api.dto.integracao.github.GithubIntegracaoHubResponse;
import com.notificacao_api.dto.integracao.github.GithubIntegracaoIssueCommentModuloResponse;
import com.notificacao_api.dto.integracao.github.GithubIntegracaoModuloStatusResponse;
import com.notificacao_api.dto.integracao.github.GithubIntegracaoProjectsV2PatchRequest;
import com.notificacao_api.dto.integracao.github.GithubIntegracaoProjectsV2Response;
import com.notificacao_api.enums.GithubIntegracaoModulo;
import com.notificacao_api.enums.RecursoFeature;
import com.notificacao_api.model.github.GithubOrganizacaoConfig;
import com.notificacao_api.model.github.OrganizacaoGithubIntegracao;
import com.notificacao_api.model.github.OrganizacaoGithubModulo;
import com.notificacao_api.repository.OrganizacaoGithubModuloRepository;
import com.notificacao_api.service.FeatureFlagService;
import com.notificacao_api.service.OrganizacaoGithubAppCredentialsService;
import com.notificacao_api.service.OrganizacaoGithubGraphqlTokenService;
import com.notificacao_api.service.OrganizacaoGithubIntegracaoSettingsService;
import com.notificacao_api.service.OrganizacaoGithubIntegracaoSettingsService.OrganizacaoGithubIntegracaoRequest;
import com.notificacao_api.service.github.GithubWhatsappOptInSupport;

@Service
public class GithubIntegracaoAppService {

    private final FeatureFlagService featureFlagService;
    private final GithubIntegracaoConfigService githubIntegracaoConfigService;
    private final OrganizacaoGithubModuloRepository moduloRepository;
    private final OrganizacaoGithubGraphqlTokenService graphqlTokenService;
    private final OrganizacaoGithubAppCredentialsService appCredentialsService;
    private final OrganizacaoGithubIntegracaoSettingsService integracaoSettingsService;
    private final GithubWebhookTemplatesPorCenarioService templatesPorCenarioService;
    private final ObjectMapper objectMapper;

    public GithubIntegracaoAppService(
            FeatureFlagService featureFlagService,
            GithubIntegracaoConfigService githubIntegracaoConfigService,
            OrganizacaoGithubModuloRepository moduloRepository,
            OrganizacaoGithubGraphqlTokenService graphqlTokenService,
            OrganizacaoGithubAppCredentialsService appCredentialsService,
            OrganizacaoGithubIntegracaoSettingsService integracaoSettingsService,
            GithubWebhookTemplatesPorCenarioService templatesPorCenarioService,
            ObjectMapper objectMapper) {
        this.featureFlagService = featureFlagService;
        this.githubIntegracaoConfigService = githubIntegracaoConfigService;
        this.moduloRepository = moduloRepository;
        this.graphqlTokenService = graphqlTokenService;
        this.appCredentialsService = appCredentialsService;
        this.integracaoSettingsService = integracaoSettingsService;
        this.templatesPorCenarioService = templatesPorCenarioService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public GithubIntegracaoHubResponse obterHub(Long idOrganizacao) {
        githubIntegracaoConfigService.garantirRegistros(idOrganizacao);
        boolean feature = featureFlagService.estaHabilitado(idOrganizacao, RecursoFeature.GITHUB_WEBHOOK);
        List<OrganizacaoGithubModulo> modulos = moduloRepository.findByIdOrganizacao(idOrganizacao);
        List<GithubIntegracaoModuloStatusResponse> status = List.of(
                statusModulo(modulos, GithubIntegracaoModulo.PROJECTS_V2, tituloModulo(GithubIntegracaoModulo.PROJECTS_V2), moduloImplementado(GithubIntegracaoModulo.PROJECTS_V2)),
                statusModulo(modulos, GithubIntegracaoModulo.ISSUE_COMMENT, tituloModulo(GithubIntegracaoModulo.ISSUE_COMMENT), moduloImplementado(GithubIntegracaoModulo.ISSUE_COMMENT)));
        return new GithubIntegracaoHubResponse(idOrganizacao, feature, status);
    }

    @Transactional(readOnly = true)
    public GithubIntegracaoCompartilhadoResponse obterCompartilhado(Long idOrganizacao) {
        OrganizacaoGithubIntegracao integracao = githubIntegracaoConfigService.obterIntegracao(idOrganizacao);
        GithubOrganizacaoConfig config = githubIntegracaoConfigService.obterConfiguracao(idOrganizacao);
        var settings = integracaoSettingsService.resolver(integracao);
        return new GithubIntegracaoCompartilhadoResponse(
                idOrganizacao,
                GithubWhatsappOptInSupport.resolverFraseAtivacao(config.getDsGithubFraseAtivacaoWhatsapp()),
                integracao.getDsOrganizationLogin(),
                graphqlTokenService.estaConfigurado(integracao),
                integracao.getNuGithubAppId(),
                integracao.getNuGithubInstallationId(),
                appCredentialsService.privateKeyConfigurada(integracao),
                settings.graphqlUrl(),
                settings.apiBaseUrl(),
                settings.httpConnectTimeoutMs(),
                settings.httpReadTimeoutMs(),
                settings.installationTokenSkewSegundos());
    }

    @Transactional
    public GithubIntegracaoCompartilhadoResponse patchCompartilhado(
            Long idOrganizacao, GithubIntegracaoCompartilhadoPatchRequest request) {
        OrganizacaoGithubIntegracao integracao = githubIntegracaoConfigService.obterIntegracao(idOrganizacao);
        if (request.dsGithubFraseAtivacaoWhatsapp() != null) {
            githubIntegracaoConfigService.aplicarFraseAtivacao(idOrganizacao, request.dsGithubFraseAtivacaoWhatsapp());
            integracao = githubIntegracaoConfigService.obterIntegracao(idOrganizacao);
        }
        if (request.dsGithubOrganizationLogin() != null) {
            integracao.setDsOrganizationLogin(request.dsGithubOrganizationLogin().trim());
        }
        graphqlTokenService.aplicar(integracao, request.githubGraphqlToken());
        if (request.githubAppId() != null) {
            appCredentialsService.aplicarAppId(integracao, request.githubAppId());
        }
        if (request.githubInstallationId() != null) {
            appCredentialsService.aplicarInstallationId(integracao, request.githubInstallationId());
        }
        appCredentialsService.aplicarPrivateKeyPem(integracao, request.githubAppPrivateKey());
        integracaoSettingsService.aplicarEndpoints(
                integracao,
                new OrganizacaoGithubIntegracaoRequest(
                        request.githubGraphqlUrl(),
                        request.githubApiBaseUrl(),
                        request.githubHttpConnectTimeoutMs(),
                        request.githubHttpReadTimeoutMs(),
                        request.githubInstallationTokenSkewSegundos()));
        githubIntegracaoConfigService.salvarIntegracao(integracao);
        return obterCompartilhado(idOrganizacao);
    }

    @Transactional(readOnly = true)
    public GithubIntegracaoProjectsV2Response obterProjectsV2(Long idOrganizacao) {
        GithubOrganizacaoConfig config = githubIntegracaoConfigService.obterConfiguracao(idOrganizacao);
        OrganizacaoGithubModulo modulo = moduloRepository
                .findByIdOrganizacaoAndDsModulo(idOrganizacao, GithubIntegracaoModulo.PROJECTS_V2.codigo())
                .orElseThrow();
        Map<String, GithubTemplatePorCenarioDto> templates = templatesPorCenarioService.ler(config);
        return toProjectsV2Response(modulo, config, templates);
    }

    @Transactional
    public GithubIntegracaoProjectsV2Response patchProjectsV2(
            Long idOrganizacao, GithubIntegracaoProjectsV2PatchRequest patch) {
        GithubOrganizacaoConfig atual = githubIntegracaoConfigService.obterConfiguracao(idOrganizacao);
        mesclarProjectsV2(atual, patch);
        if (patch.githubTemplatesPorCenario() != null) {
            templatesPorCenarioService.aplicar(atual, patch.githubTemplatesPorCenario());
        }
        GithubOrganizacaoConfig salvo = githubIntegracaoConfigService.salvarProjectsV2(idOrganizacao, atual);
        OrganizacaoGithubModulo modulo = moduloRepository
                .findByIdOrganizacaoAndDsModulo(idOrganizacao, GithubIntegracaoModulo.PROJECTS_V2.codigo())
                .orElseThrow();
        return toProjectsV2Response(modulo, salvo, templatesPorCenarioService.ler(salvo));
    }

    @Transactional(readOnly = true)
    public GithubIntegracaoIssueCommentModuloResponse obterIssueComment(Long idOrganizacao) {
        githubIntegracaoConfigService.garantirRegistros(idOrganizacao);
        OrganizacaoGithubModulo modulo = moduloRepository
                .findByIdOrganizacaoAndDsModulo(idOrganizacao, GithubIntegracaoModulo.ISSUE_COMMENT.codigo())
                .orElseThrow();
        int versao = 1;
        try {
            JsonNode node = objectMapper.readTree(modulo.getDsConfigJson());
            if (node.has("versao")) {
                versao = node.get("versao").asInt(1);
            }
        } catch (Exception ignored) {
            // mantém default
        }
        return new GithubIntegracaoIssueCommentModuloResponse(
                Boolean.TRUE.equals(modulo.getFlHabilitado()), false, versao);
    }

    @Transactional
    public GithubIntegracaoModuloStatusResponse patchModuloHabilitado(
            Long idOrganizacao, String codigoModulo, boolean habilitado) {
        GithubIntegracaoModulo tipo = GithubIntegracaoModulo.fromString(codigoModulo);
        githubIntegracaoConfigService.garantirRegistros(idOrganizacao);
        OrganizacaoGithubModulo modulo = moduloRepository
                .findByIdOrganizacaoAndDsModulo(idOrganizacao, tipo.codigo())
                .orElseThrow();
        modulo.setFlHabilitado(habilitado);
        moduloRepository.save(modulo);
        return statusModulo(List.of(modulo), tipo, tituloModulo(tipo), moduloImplementado(tipo));
    }

    private static String tituloModulo(GithubIntegracaoModulo tipo) {
        return switch (tipo) {
            case PROJECTS_V2 -> "Project v2 (kanban)";
            case ISSUE_COMMENT -> "Comentários em issues";
        };
    }

    private static boolean moduloImplementado(GithubIntegracaoModulo tipo) {
        return tipo == GithubIntegracaoModulo.PROJECTS_V2;
    }

    private static GithubIntegracaoModuloStatusResponse statusModulo(
            List<OrganizacaoGithubModulo> modulos,
            GithubIntegracaoModulo tipo,
            String titulo,
            boolean implementado) {
        boolean habilitado = modulos.stream()
                .filter(m -> tipo.codigo().equals(m.getDsModulo()))
                .findFirst()
                .map(m -> Boolean.TRUE.equals(m.getFlHabilitado()))
                .orElse(false);
        return new GithubIntegracaoModuloStatusResponse(tipo.codigo(), titulo, habilitado, implementado);
    }

    private static GithubIntegracaoProjectsV2Response toProjectsV2Response(
            OrganizacaoGithubModulo modulo,
            GithubOrganizacaoConfig config,
            Map<String, GithubTemplatePorCenarioDto> templates) {
        return new GithubIntegracaoProjectsV2Response(
                Boolean.TRUE.equals(modulo.getFlHabilitado()),
                config.getDsGithubProjectV2NodeId(),
                config.getNuGithubProjectV2Number(),
                config.getDsGithubStatusDisparo(),
                config.getDsGithubStatusDisparoGatilhos(),
                config.getDsGithubRegrasPorStatus(),
                config.getDsGithubTemplateAssuntoWhatsapp(),
                config.getDsGithubTemplateMensagemWhatsapp(),
                templates,
                config.getGithubNaoNotificarMovimentador(),
                config.getGithubNotificarStatusAlterado(),
                config.getGithubNotificarTarefaCriada(),
                config.getGithubNotificarResponsavelAlterado(),
                config.getGithubNotificarTarefaAtribuida(),
                config.getGithubIgnorarSemResponsavel(),
                config.getDsGithubDestinatariosModo(),
                config.getDsGithubDestinatariosExtras(),
                config.getGithubNotificarIssueFechadaReaberta(),
                config.getGithubNotificarIssueLabel(),
                config.getGithubNotificarSomenteCampoStatus(),
                config.getGithubNotificarReordenacao(),
                config.getGithubPrAvisarAvaliadores(),
                config.getDsGithubPrStatusDisparo(),
                config.getDsGithubPrLoginsAvaliadores(),
                config.getGithubIssueAvisarAvaliadores(),
                config.getDsGithubIssueStatusDisparo());
    }

    private static void mesclarProjectsV2(GithubOrganizacaoConfig atual, GithubIntegracaoProjectsV2PatchRequest patch) {
        if (patch.dsGithubProjectV2NodeId() != null) {
            atual.setDsGithubProjectV2NodeId(patch.dsGithubProjectV2NodeId());
        }
        if (patch.nuGithubProjectV2Number() != null) {
            atual.setNuGithubProjectV2Number(patch.nuGithubProjectV2Number());
        }
        if (patch.dsGithubStatusDisparo() != null) {
            atual.setDsGithubStatusDisparo(patch.dsGithubStatusDisparo());
        }
        if (patch.dsGithubStatusDisparoGatilhos() != null) {
            atual.setDsGithubStatusDisparoGatilhos(patch.dsGithubStatusDisparoGatilhos());
        }
        if (patch.dsGithubRegrasPorStatus() != null) {
            atual.setDsGithubRegrasPorStatus(patch.dsGithubRegrasPorStatus());
        }
        if (patch.dsGithubTemplateAssuntoWhatsapp() != null) {
            atual.setDsGithubTemplateAssuntoWhatsapp(patch.dsGithubTemplateAssuntoWhatsapp());
        }
        if (patch.dsGithubTemplateMensagemWhatsapp() != null) {
            atual.setDsGithubTemplateMensagemWhatsapp(patch.dsGithubTemplateMensagemWhatsapp());
        }
        if (patch.githubNaoNotificarMovimentador() != null) {
            atual.setGithubNaoNotificarMovimentador(patch.githubNaoNotificarMovimentador());
        }
        if (patch.githubNotificarStatusAlterado() != null) {
            atual.setGithubNotificarStatusAlterado(patch.githubNotificarStatusAlterado());
        }
        if (patch.githubNotificarTarefaCriada() != null) {
            atual.setGithubNotificarTarefaCriada(patch.githubNotificarTarefaCriada());
        }
        if (patch.githubNotificarResponsavelAlterado() != null) {
            atual.setGithubNotificarResponsavelAlterado(patch.githubNotificarResponsavelAlterado());
        }
        if (patch.githubNotificarTarefaAtribuida() != null) {
            atual.setGithubNotificarTarefaAtribuida(patch.githubNotificarTarefaAtribuida());
        }
        if (patch.githubIgnorarSemResponsavel() != null) {
            atual.setGithubIgnorarSemResponsavel(patch.githubIgnorarSemResponsavel());
        }
        if (patch.dsGithubDestinatariosModo() != null) {
            atual.setDsGithubDestinatariosModo(patch.dsGithubDestinatariosModo());
        }
        if (patch.dsGithubDestinatariosExtras() != null) {
            atual.setDsGithubDestinatariosExtras(patch.dsGithubDestinatariosExtras());
        }
        if (patch.githubNotificarIssueFechadaReaberta() != null) {
            atual.setGithubNotificarIssueFechadaReaberta(patch.githubNotificarIssueFechadaReaberta());
        }
        if (patch.githubNotificarIssueLabel() != null) {
            atual.setGithubNotificarIssueLabel(patch.githubNotificarIssueLabel());
        }
        if (patch.githubNotificarSomenteCampoStatus() != null) {
            atual.setGithubNotificarSomenteCampoStatus(patch.githubNotificarSomenteCampoStatus());
        }
        if (patch.githubNotificarReordenacao() != null) {
            atual.setGithubNotificarReordenacao(patch.githubNotificarReordenacao());
        }
        if (patch.githubPrAvisarAvaliadores() != null) {
            atual.setGithubPrAvisarAvaliadores(patch.githubPrAvisarAvaliadores());
        }
        if (patch.dsGithubPrStatusDisparo() != null) {
            atual.setDsGithubPrStatusDisparo(patch.dsGithubPrStatusDisparo());
        }
        if (patch.dsGithubPrLoginsAvaliadores() != null) {
            atual.setDsGithubPrLoginsAvaliadores(patch.dsGithubPrLoginsAvaliadores());
        }
        if (patch.githubIssueAvisarAvaliadores() != null) {
            atual.setGithubIssueAvisarAvaliadores(patch.githubIssueAvisarAvaliadores());
        }
        if (patch.dsGithubIssueStatusDisparo() != null) {
            atual.setDsGithubIssueStatusDisparo(patch.dsGithubIssueStatusDisparo());
        }
    }
}
