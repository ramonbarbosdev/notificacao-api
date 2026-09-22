package com.notificacao_api.service.github;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.notificacao_api.enums.GithubIntegracaoModulo;
import com.notificacao_api.model.github.GithubOrganizacaoConfig;
import com.notificacao_api.model.github.OrganizacaoGithubIntegracao;
import com.notificacao_api.model.github.OrganizacaoGithubModulo;
import com.notificacao_api.repository.OrganizacaoGithubIntegracaoRepository;
import com.notificacao_api.repository.OrganizacaoGithubModuloRepository;
import com.notificacao_api.service.github.config.GithubProjectsV2ConfigJson;

@Service
public class GithubIntegracaoConfigService {

    private final OrganizacaoGithubIntegracaoRepository integracaoRepository;
    private final OrganizacaoGithubModuloRepository moduloRepository;
    private final ObjectMapper objectMapper;
    private final GithubRegrasPorStatusService githubRegrasPorStatusService;

    public GithubIntegracaoConfigService(
            OrganizacaoGithubIntegracaoRepository integracaoRepository,
            OrganizacaoGithubModuloRepository moduloRepository,
            ObjectMapper objectMapper,
            GithubRegrasPorStatusService githubRegrasPorStatusService) {
        this.integracaoRepository = integracaoRepository;
        this.moduloRepository = moduloRepository;
        this.objectMapper = objectMapper;
        this.githubRegrasPorStatusService = githubRegrasPorStatusService;
    }

    @Transactional
    public void garantirRegistros(Long idOrganizacao) {
        integracaoRepository.findByIdOrganizacao(idOrganizacao).orElseGet(() -> {
            OrganizacaoGithubIntegracao row = new OrganizacaoGithubIntegracao();
            row.setIdOrganizacao(idOrganizacao);
            return integracaoRepository.save(row);
        });
        garantirModulo(idOrganizacao, GithubIntegracaoModulo.PROJECTS_V2, true, defaultProjectsV2Json());
        garantirModulo(idOrganizacao, GithubIntegracaoModulo.ISSUE_COMMENT, false, "{\"versao\":1}");
    }

    @Transactional
    public GithubOrganizacaoConfig obterConfiguracao(Long idOrganizacao) {
        garantirRegistros(idOrganizacao);
        OrganizacaoGithubIntegracao integracao = integracaoRepository
                .findByIdOrganizacao(idOrganizacao)
                .orElseThrow();
        OrganizacaoGithubModulo modulo = moduloRepository
                .findByIdOrganizacaoAndDsModulo(idOrganizacao, GithubIntegracaoModulo.PROJECTS_V2.codigo())
                .orElseThrow();
        GithubProjectsV2ConfigJson pv2 = lerProjectsV2Json(modulo.getDsConfigJson());
        return montarAgregado(idOrganizacao, integracao, pv2);
    }

    @Transactional(readOnly = true)
    public OrganizacaoGithubIntegracao obterIntegracao(Long idOrganizacao) {
        garantirRegistros(idOrganizacao);
        return integracaoRepository.findByIdOrganizacao(idOrganizacao).orElseThrow();
    }

    @Transactional
    public OrganizacaoGithubIntegracao salvarIntegracao(OrganizacaoGithubIntegracao integracao) {
        return integracaoRepository.save(integracao);
    }

    @Transactional
    public GithubOrganizacaoConfig salvarProjectsV2(Long idOrganizacao, GithubOrganizacaoConfig dados) {
        garantirRegistros(idOrganizacao);
        OrganizacaoGithubIntegracao integracao = integracaoRepository.findByIdOrganizacao(idOrganizacao).orElseThrow();
        aplicarIntegracaoNoRow(integracao, dados);
        integracaoRepository.save(integracao);

        OrganizacaoGithubModulo modulo = moduloRepository
                .findByIdOrganizacaoAndDsModulo(idOrganizacao, GithubIntegracaoModulo.PROJECTS_V2.codigo())
                .orElseThrow();
        GithubProjectsV2ConfigJson json = extrairProjectsV2Json(dados);
        if (StringUtils.hasText(dados.getDsGithubRegrasPorStatus())) {
            githubRegrasPorStatusService.aplicarJson(dados, dados.getDsGithubRegrasPorStatus());
            json.regrasPorStatus = dados.getDsGithubRegrasPorStatus();
            json.statusDisparo = dados.getDsGithubStatusDisparo();
            json.prStatusDisparo = dados.getDsGithubPrStatusDisparo();
            json.issueStatusDisparo = dados.getDsGithubIssueStatusDisparo();
        }
        modulo.setFlHabilitado(true);
        modulo.setDsConfigJson(serializarProjectsV2(json));
        moduloRepository.save(modulo);
        return obterConfiguracao(idOrganizacao);
    }

    @Transactional
    public void aplicarFraseAtivacao(Long idOrganizacao, String frase) {
        OrganizacaoGithubIntegracao integracao = integracaoRepository
                .findByIdOrganizacao(idOrganizacao)
                .orElseGet(() -> {
                    garantirRegistros(idOrganizacao);
                    return integracaoRepository.findByIdOrganizacao(idOrganizacao).orElseThrow();
                });
        integracao.setDsFraseAtivacaoWhatsapp(frase);
        integracaoRepository.save(integracao);
    }

    private void garantirModulo(Long idOrganizacao, GithubIntegracaoModulo modulo, boolean habilitado, String json) {
        moduloRepository
                .findByIdOrganizacaoAndDsModulo(idOrganizacao, modulo.codigo())
                .orElseGet(() -> {
                    OrganizacaoGithubModulo row = new OrganizacaoGithubModulo();
                    row.setIdOrganizacao(idOrganizacao);
                    row.setDsModulo(modulo.codigo());
                    row.setFlHabilitado(habilitado);
                    row.setDsConfigJson(json);
                    return moduloRepository.save(row);
                });
    }

    private static String defaultProjectsV2Json() {
        return "{\"versao\":1}";
    }

    private GithubProjectsV2ConfigJson lerProjectsV2Json(String raw) {
        if (!StringUtils.hasText(raw)) {
            return new GithubProjectsV2ConfigJson();
        }
        try {
            return objectMapper.readValue(raw, GithubProjectsV2ConfigJson.class);
        } catch (JsonProcessingException ex) {
            return new GithubProjectsV2ConfigJson();
        }
    }

    private String serializarProjectsV2(GithubProjectsV2ConfigJson json) {
        try {
            return objectMapper.writeValueAsString(json);
        } catch (JsonProcessingException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Falha ao serializar modulo Project v2.");
        }
    }

    private GithubOrganizacaoConfig montarAgregado(
            Long idOrganizacao, OrganizacaoGithubIntegracao integracao, GithubProjectsV2ConfigJson pv2) {
        GithubOrganizacaoConfig c = new GithubOrganizacaoConfig();
        c.setIdOrganizacao(idOrganizacao);
        c.setDsGithubFraseAtivacaoWhatsapp(integracao.getDsFraseAtivacaoWhatsapp());
        c.setDsGithubOrganizationLogin(integracao.getDsOrganizationLogin());
        c.setDsGithubGraphqlTokenEnc(integracao.getDsGraphqlTokenEnc());
        c.setNuGithubAppId(integracao.getNuGithubAppId());
        c.setDsGithubAppPrivateKeyEnc(integracao.getDsGithubAppPrivateKeyEnc());
        c.setNuGithubInstallationId(integracao.getNuGithubInstallationId());
        c.setDsGithubGraphqlUrl(integracao.getDsGithubGraphqlUrl());
        c.setDsGithubApiBaseUrl(integracao.getDsGithubApiBaseUrl());
        c.setNuGithubHttpConnectTimeoutMs(integracao.getNuGithubHttpConnectTimeoutMs());
        c.setNuGithubHttpReadTimeoutMs(integracao.getNuGithubHttpReadTimeoutMs());
        c.setNuGithubInstallationTokenSkewSegundos(integracao.getNuGithubInstallationTokenSkewSegundos());

        c.setDsGithubProjectV2NodeId(pv2.projectV2NodeId);
        c.setNuGithubProjectV2Number(pv2.projectV2Number);
        c.setDsGithubStatusDisparo(pv2.statusDisparo);
        c.setDsGithubStatusDisparoGatilhos(pv2.statusDisparoGatilhos);
        c.setDsGithubRegrasPorStatus(pv2.regrasPorStatus);
        c.setDsGithubTemplateAssuntoWhatsapp(pv2.templateAssuntoWhatsapp);
        c.setDsGithubTemplateMensagemWhatsapp(pv2.templateMensagemWhatsapp);
        if (pv2.templatesPorCenario != null) {
            try {
                c.setDsGithubTemplatesPorCenario(objectMapper.writeValueAsString(pv2.templatesPorCenario));
            } catch (JsonProcessingException ignored) {
                c.setDsGithubTemplatesPorCenario(null);
            }
        }
        c.setGithubNaoNotificarMovimentador(pv2.naoNotificarMovimentador);
        c.setGithubNotificarStatusAlterado(pv2.notificarStatusAlterado);
        c.setGithubNotificarTarefaCriada(pv2.notificarTarefaCriada);
        c.setGithubNotificarResponsavelAlterado(pv2.notificarResponsavelAlterado);
        c.setGithubNotificarTarefaAtribuida(pv2.notificarTarefaAtribuida);
        c.setGithubIgnorarSemResponsavel(pv2.ignorarSemResponsavel);
        c.setDsGithubDestinatariosModo(pv2.destinatariosModo);
        c.setDsGithubDestinatariosExtras(pv2.destinatariosExtras);
        c.setGithubNotificarIssueFechadaReaberta(pv2.notificarIssueFechadaReaberta);
        c.setGithubNotificarIssueLabel(pv2.notificarIssueLabel);
        c.setGithubNotificarSomenteCampoStatus(pv2.notificarSomenteCampoStatus);
        c.setGithubNotificarReordenacao(pv2.notificarReordenacao);
        c.setGithubPrAvisarAvaliadores(pv2.prAvisarAvaliadores);
        c.setDsGithubPrStatusDisparo(pv2.prStatusDisparo);
        c.setDsGithubPrLoginsAvaliadores(pv2.prLoginsAvaliadores);
        c.setGithubIssueAvisarAvaliadores(pv2.issueAvisarAvaliadores);
        c.setDsGithubIssueStatusDisparo(pv2.issueStatusDisparo);
        return c;
    }

    private void aplicarIntegracaoNoRow(OrganizacaoGithubIntegracao integracao, GithubOrganizacaoConfig dados) {
        if (dados.getDsGithubFraseAtivacaoWhatsapp() != null) {
            integracao.setDsFraseAtivacaoWhatsapp(dados.getDsGithubFraseAtivacaoWhatsapp());
        }
        if (dados.getDsGithubOrganizationLogin() != null) {
            integracao.setDsOrganizationLogin(dados.getDsGithubOrganizationLogin());
        }
        if (dados.getDsGithubGraphqlTokenEnc() != null) {
            integracao.setDsGraphqlTokenEnc(dados.getDsGithubGraphqlTokenEnc());
        }
        if (dados.getNuGithubAppId() != null) {
            integracao.setNuGithubAppId(dados.getNuGithubAppId());
        }
        if (dados.getDsGithubAppPrivateKeyEnc() != null) {
            integracao.setDsGithubAppPrivateKeyEnc(dados.getDsGithubAppPrivateKeyEnc());
        }
        if (dados.getNuGithubInstallationId() != null) {
            integracao.setNuGithubInstallationId(dados.getNuGithubInstallationId());
        }
        if (dados.getDsGithubGraphqlUrl() != null) {
            integracao.setDsGithubGraphqlUrl(dados.getDsGithubGraphqlUrl());
        }
        if (dados.getDsGithubApiBaseUrl() != null) {
            integracao.setDsGithubApiBaseUrl(dados.getDsGithubApiBaseUrl());
        }
        if (dados.getNuGithubHttpConnectTimeoutMs() != null) {
            integracao.setNuGithubHttpConnectTimeoutMs(dados.getNuGithubHttpConnectTimeoutMs());
        }
        if (dados.getNuGithubHttpReadTimeoutMs() != null) {
            integracao.setNuGithubHttpReadTimeoutMs(dados.getNuGithubHttpReadTimeoutMs());
        }
        if (dados.getNuGithubInstallationTokenSkewSegundos() != null) {
            integracao.setNuGithubInstallationTokenSkewSegundos(dados.getNuGithubInstallationTokenSkewSegundos());
        }
    }

    private GithubProjectsV2ConfigJson extrairProjectsV2Json(GithubOrganizacaoConfig dados) {
        GithubProjectsV2ConfigJson json = new GithubProjectsV2ConfigJson();
        json.projectV2NodeId = dados.getDsGithubProjectV2NodeId();
        json.projectV2Number = dados.getNuGithubProjectV2Number();
        json.statusDisparo = dados.getDsGithubStatusDisparo();
        json.statusDisparoGatilhos = dados.getDsGithubStatusDisparoGatilhos();
        json.regrasPorStatus = dados.getDsGithubRegrasPorStatus();
        json.templateAssuntoWhatsapp = dados.getDsGithubTemplateAssuntoWhatsapp();
        json.templateMensagemWhatsapp = dados.getDsGithubTemplateMensagemWhatsapp();
        if (StringUtils.hasText(dados.getDsGithubTemplatesPorCenario())) {
            try {
                json.templatesPorCenario = objectMapper.readTree(dados.getDsGithubTemplatesPorCenario());
            } catch (JsonProcessingException ignored) {
                json.templatesPorCenario = null;
            }
        }
        json.naoNotificarMovimentador = dados.getGithubNaoNotificarMovimentador();
        json.notificarStatusAlterado = dados.getGithubNotificarStatusAlterado();
        json.notificarTarefaCriada = dados.getGithubNotificarTarefaCriada();
        json.notificarResponsavelAlterado = dados.getGithubNotificarResponsavelAlterado();
        json.notificarTarefaAtribuida = dados.getGithubNotificarTarefaAtribuida();
        json.ignorarSemResponsavel = dados.getGithubIgnorarSemResponsavel();
        json.destinatariosModo = dados.getDsGithubDestinatariosModo();
        json.destinatariosExtras = dados.getDsGithubDestinatariosExtras();
        json.notificarIssueFechadaReaberta = dados.getGithubNotificarIssueFechadaReaberta();
        json.notificarIssueLabel = dados.getGithubNotificarIssueLabel();
        json.notificarSomenteCampoStatus = dados.getGithubNotificarSomenteCampoStatus();
        json.notificarReordenacao = dados.getGithubNotificarReordenacao();
        json.prAvisarAvaliadores = dados.getGithubPrAvisarAvaliadores();
        json.prStatusDisparo = dados.getDsGithubPrStatusDisparo();
        json.prLoginsAvaliadores = dados.getDsGithubPrLoginsAvaliadores();
        json.issueAvisarAvaliadores = dados.getGithubIssueAvisarAvaliadores();
        json.issueStatusDisparo = dados.getDsGithubIssueStatusDisparo();
        return json;
    }
}
