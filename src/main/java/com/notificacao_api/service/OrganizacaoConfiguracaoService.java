package com.notificacao_api.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.notificacao_api.enums.GithubDestinatariosModo;
import com.notificacao_api.service.github.GithubWhatsappOptInSupport;

import com.notificacao_api.dto.configuracao.OrganizacaoConfiguracaoRequest;
import com.notificacao_api.dto.configuracao.OrganizacaoConfiguracaoResponse;
import com.notificacao_api.dto.integracao.WhatsappWebhookInboundRequest;
import com.notificacao_api.dto.integracao.WhatsappWebhookInboundResponse;
import com.notificacao_api.model.OrganizacaoConfiguracao;
import com.notificacao_api.repository.OrganizacaoConfiguracaoRepository;

@Service
public class OrganizacaoConfiguracaoService {

    private final OrganizacaoConfiguracaoRepository repository;
    private final TenantContextService tenantContextService;
    private final AuditoriaEventoService auditoriaService;
    private final OrganizacaoWebhookInboundService webhookInboundService;
    private final OrganizacaoGithubGraphqlTokenService githubGraphqlTokenService;
    private final OrganizacaoGithubAppCredentialsService githubAppCredentialsService;
    private final OrganizacaoGithubIntegracaoSettingsService githubIntegracaoSettingsService;

    public OrganizacaoConfiguracaoService(
            OrganizacaoConfiguracaoRepository repository,
            TenantContextService tenantContextService,
            AuditoriaEventoService auditoriaService,
            OrganizacaoWebhookInboundService webhookInboundService,
            OrganizacaoGithubGraphqlTokenService githubGraphqlTokenService,
            OrganizacaoGithubAppCredentialsService githubAppCredentialsService,
            OrganizacaoGithubIntegracaoSettingsService githubIntegracaoSettingsService) {
        this.repository = repository;
        this.tenantContextService = tenantContextService;
        this.auditoriaService = auditoriaService;
        this.webhookInboundService = webhookInboundService;
        this.githubGraphqlTokenService = githubGraphqlTokenService;
        this.githubAppCredentialsService = githubAppCredentialsService;
        this.githubIntegracaoSettingsService = githubIntegracaoSettingsService;
    }

    @Transactional
    public OrganizacaoConfiguracao criarPadrao(Long idOrganizacao, String nomeExibicao) {
        return repository.findByIdOrganizacao(idOrganizacao)
                .orElseGet(() -> {
                    OrganizacaoConfiguracao config = new OrganizacaoConfiguracao();
                    config.setIdOrganizacao(idOrganizacao);
                    config.setNmExibicao(nomeExibicao);
                    return repository.save(config);
                });
    }

    @Transactional(readOnly = true)
    public OrganizacaoConfiguracaoResponse buscarAtual() {
        Long idOrganizacao = tenantContextService.idOrganizacaoObrigatoria();
        return toResponse(repository.findByIdOrganizacao(idOrganizacao).orElseGet(() -> criarPadrao(idOrganizacao, null)));
    }

    @Transactional
    public OrganizacaoConfiguracaoResponse atualizarAtual(OrganizacaoConfiguracaoRequest request) {
        Long idOrganizacao = tenantContextService.idOrganizacaoObrigatoria();
        OrganizacaoConfiguracao config = repository.findByIdOrganizacao(idOrganizacao).orElseGet(() -> criarPadrao(idOrganizacao, null));
        OrganizacaoConfiguracaoResponse antes = toResponse(config);
        aplicar(config, request);
        OrganizacaoConfiguracaoResponse depois = toResponse(repository.save(config));
        auditoriaService.registrar(idOrganizacao, "CONFIGURACAO_ORGANIZACAO", "ATUALIZAR", "Configuracao da organizacao alterada.", antes, depois);
        return depois;
    }

    @Transactional
    public OrganizacaoConfiguracaoResponse atualizarEmailAlertas(String dsEmailAlertas) {
        Long idOrganizacao = tenantContextService.idOrganizacaoObrigatoria();
        OrganizacaoConfiguracao config = repository.findByIdOrganizacao(idOrganizacao)
                .orElseGet(() -> criarPadrao(idOrganizacao, null));
        config.setDsEmailAlertas(dsEmailAlertas != null ? dsEmailAlertas.trim() : null);
        return toResponse(repository.save(config));
    }

    @Transactional(readOnly = true)
    public WhatsappWebhookInboundResponse buscarWebhookInbound() {
        Long idOrganizacao = tenantContextService.idOrganizacaoObrigatoria();
        OrganizacaoConfiguracao config = repository.findByIdOrganizacao(idOrganizacao)
                .orElseGet(() -> criarPadrao(idOrganizacao, null));
        return webhookInboundService.toResponse(config);
    }

    @Transactional
    public WhatsappWebhookInboundResponse atualizarWebhookInbound(WhatsappWebhookInboundRequest request) {
        Long idOrganizacao = tenantContextService.idOrganizacaoObrigatoria();
        OrganizacaoConfiguracao config = repository.findByIdOrganizacao(idOrganizacao)
                .orElseGet(() -> criarPadrao(idOrganizacao, null));
        WhatsappWebhookInboundResponse antes = webhookInboundService.toResponse(config);
        webhookInboundService.aplicar(
                idOrganizacao,
                config,
                request.url(),
                request.habilitado(),
                request.secret());
        WhatsappWebhookInboundResponse depois = webhookInboundService.toResponse(repository.save(config));
        auditoriaService.registrar(
                idOrganizacao,
                "CONFIGURACAO_ORGANIZACAO",
                "ATUALIZAR_WEBHOOK_INBOUND",
                "Webhook inbound WhatsApp alterado.",
                antes,
                depois);
        return depois;
    }

    @Transactional(readOnly = true)
    public OrganizacaoConfiguracao buscarPorOrganizacao(Long idOrganizacao) {
        return repository.findByIdOrganizacao(idOrganizacao).orElse(null);
    }

    @Transactional(readOnly = true)
    public String fraseAtivacaoGithubWhatsapp(Long idOrganizacao) {
        OrganizacaoConfiguracao config = buscarPorOrganizacao(idOrganizacao);
        return GithubWhatsappOptInSupport.resolverFraseAtivacao(
                config != null ? config.getDsGithubFraseAtivacaoWhatsapp() : null);
    }

    private void aplicar(OrganizacaoConfiguracao c, OrganizacaoConfiguracaoRequest r) {
        c.setNmExibicao(r.nmExibicao());
        c.setDsLogoUrl(r.dsLogoUrl());
        c.setDsIdioma(r.dsIdioma());
        c.setTimezone(r.timezone());
        c.setNuTelefoneOperacional(r.nuTelefoneOperacional());
        c.setDsEmailOperacional(r.dsEmailOperacional());
        c.setDsEmailAlertas(r.dsEmailAlertas());
        if (r.whatsappReconexaoAutomatica() != null) c.setWhatsappReconexaoAutomatica(r.whatsappReconexaoAutomatica());
        if (r.whatsappDelayMinSegundos() != null) c.setWhatsappDelayMinSegundos(r.whatsappDelayMinSegundos());
        if (r.whatsappDelayMaxSegundos() != null) c.setWhatsappDelayMaxSegundos(r.whatsappDelayMaxSegundos());
        if (r.whatsappSimularDigitando() != null) c.setWhatsappSimularDigitando(r.whatsappSimularDigitando());
        if (r.whatsappLimitePorMinuto() != null) c.setWhatsappLimitePorMinuto(r.whatsappLimitePorMinuto());
        if (r.whatsappLimitePorDia() != null) c.setWhatsappLimitePorDia(r.whatsappLimitePorDia());
        if (r.whatsappModoEnvio() != null) c.setWhatsappModoEnvio(r.whatsappModoEnvio());
        if (r.templatesVersionamento() != null) c.setTemplatesVersionamento(r.templatesVersionamento());
        if (r.templatesExigirAprovacao() != null) c.setTemplatesExigirAprovacao(r.templatesExigirAprovacao());
        if (r.templatesValidarVariaveis() != null) c.setTemplatesValidarVariaveis(r.templatesValidarVariaveis());
        if (r.retryAutomatico() != null) c.setRetryAutomatico(r.retryAutomatico());
        if (r.retryTentativas() != null) c.setRetryTentativas(r.retryTentativas());
        if (r.retryIntervaloSegundos() != null) c.setRetryIntervaloSegundos(r.retryIntervaloSegundos());
        if (r.prioridadePadrao() != null) c.setPrioridadePadrao(r.prioridadePadrao());
        if (r.expiracaoFilaHoras() != null) c.setExpiracaoFilaHoras(r.expiracaoFilaHoras());
        if (r.auditoriaHabilitada() != null) c.setAuditoriaHabilitada(r.auditoriaHabilitada());
        if (r.webhookInboundUrl() != null || r.webhookInboundHabilitado() != null || r.webhookInboundSecret() != null) {
            webhookInboundService.aplicar(
                    c.getIdOrganizacao(),
                    c,
                    r.webhookInboundUrl() != null ? r.webhookInboundUrl() : c.getWebhookInboundUrl(),
                    r.webhookInboundHabilitado() != null ? r.webhookInboundHabilitado() : c.getWebhookInboundHabilitado(),
                    r.webhookInboundSecret());
        }
        c.setDsGithubStatusDisparo(normalizarTextoOpcional(r.dsGithubStatusDisparo()));
        if (r.dsGithubFraseAtivacaoWhatsapp() != null) {
            String frase = r.dsGithubFraseAtivacaoWhatsapp().trim();
            if (frase.isEmpty()) {
                c.setDsGithubFraseAtivacaoWhatsapp(null);
            } else if (frase.length() > 500) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Frase de ativacao GitHub WhatsApp deve ter no maximo 500 caracteres.");
            } else {
                c.setDsGithubFraseAtivacaoWhatsapp(frase);
            }
        }
        if (r.webhookRegistrarFilaSemDestinatario() != null) {
            c.setWebhookRegistrarFilaSemDestinatario(r.webhookRegistrarFilaSemDestinatario());
        }
        if (r.dsGithubTemplateAssuntoWhatsapp() != null) {
            c.setDsGithubTemplateAssuntoWhatsapp(normalizarTemplateOpcional(r.dsGithubTemplateAssuntoWhatsapp(), 500));
        }
        if (r.dsGithubTemplateMensagemWhatsapp() != null) {
            c.setDsGithubTemplateMensagemWhatsapp(normalizarTemplateOpcional(r.dsGithubTemplateMensagemWhatsapp(), 8000));
        }
        if (r.githubNaoNotificarMovimentador() != null) {
            c.setGithubNaoNotificarMovimentador(r.githubNaoNotificarMovimentador());
        }
        if (r.githubNotificarStatusAlterado() != null) {
            c.setGithubNotificarStatusAlterado(r.githubNotificarStatusAlterado());
        }
        if (r.githubNotificarTarefaCriada() != null) {
            c.setGithubNotificarTarefaCriada(r.githubNotificarTarefaCriada());
        }
        if (r.githubNotificarResponsavelAlterado() != null) {
            c.setGithubNotificarResponsavelAlterado(r.githubNotificarResponsavelAlterado());
        }
        if (r.githubNotificarTarefaAtribuida() != null) {
            c.setGithubNotificarTarefaAtribuida(r.githubNotificarTarefaAtribuida());
        }
        if (r.githubIgnorarSemResponsavel() != null) {
            c.setGithubIgnorarSemResponsavel(r.githubIgnorarSemResponsavel());
        }
        if (r.dsGithubDestinatariosModo() != null) {
            c.setDsGithubDestinatariosModo(
                    GithubDestinatariosModo.fromString(r.dsGithubDestinatariosModo()).name());
        }
        if (r.dsGithubDestinatariosExtras() != null) {
            String extras = r.dsGithubDestinatariosExtras().trim();
            c.setDsGithubDestinatariosExtras(extras.isEmpty() ? null : extras);
        }
        if (r.githubNotificarIssueFechadaReaberta() != null) {
            c.setGithubNotificarIssueFechadaReaberta(r.githubNotificarIssueFechadaReaberta());
        }
        if (r.githubNotificarIssueLabel() != null) {
            c.setGithubNotificarIssueLabel(r.githubNotificarIssueLabel());
        }
        if (r.githubNotificarSomenteCampoStatus() != null) {
            c.setGithubNotificarSomenteCampoStatus(r.githubNotificarSomenteCampoStatus());
        }
        if (r.githubPrAvisarAvaliadores() != null) {
            c.setGithubPrAvisarAvaliadores(r.githubPrAvisarAvaliadores());
        }
        c.setDsGithubPrStatusDisparo(normalizarTextoOpcional(r.dsGithubPrStatusDisparo()));
        if (r.dsGithubPrLoginsAvaliadores() != null) {
            String logins = r.dsGithubPrLoginsAvaliadores().trim();
            c.setDsGithubPrLoginsAvaliadores(logins.isEmpty() ? null : logins);
        }
        if (r.githubGraphqlToken() != null) {
            githubGraphqlTokenService.aplicar(c, r.githubGraphqlToken());
        }
        if (r.githubAppId() != null) {
            githubAppCredentialsService.aplicarAppId(c, r.githubAppId());
        }
        if (r.githubInstallationId() != null) {
            githubAppCredentialsService.aplicarInstallationId(c, r.githubInstallationId());
        }
        if (r.githubAppPrivateKey() != null) {
            githubAppCredentialsService.aplicarPrivateKeyPem(c, r.githubAppPrivateKey());
        }
        githubIntegracaoSettingsService.aplicarEndpoints(
                c,
                new OrganizacaoGithubIntegracaoSettingsService.OrganizacaoGithubIntegracaoRequest(
                        r.githubGraphqlUrl(),
                        r.githubApiBaseUrl(),
                        r.githubHttpConnectTimeoutMs(),
                        r.githubHttpReadTimeoutMs(),
                        r.githubInstallationTokenSkewSegundos()));
    }

    private static String normalizarTextoOpcional(String valor) {
        if (valor == null) {
            return null;
        }
        String texto = valor.trim();
        return texto.isEmpty() ? null : texto;
    }

    private String normalizarTemplateOpcional(String valor, int maximo) {
        if (valor == null) {
            return null;
        }
        String texto = valor.trim();
        if (texto.isEmpty()) {
            return null;
        }
        if (texto.length() > maximo) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Template GitHub WhatsApp deve ter no maximo " + maximo + " caracteres.");
        }
        return texto;
    }

    public boolean deveRegistrarFilaSemDestinatario(OrganizacaoConfiguracao configuracao) {
        if (configuracao == null || configuracao.getWebhookRegistrarFilaSemDestinatario() == null) {
            return true;
        }
        return configuracao.getWebhookRegistrarFilaSemDestinatario();
    }

    private OrganizacaoConfiguracaoResponse toResponse(OrganizacaoConfiguracao c) {
        return new OrganizacaoConfiguracaoResponse(
                c.getIdOrganizacaoConfiguracao(), c.getIdOrganizacao(), c.getNmExibicao(), c.getDsLogoUrl(),
                c.getDsIdioma(), c.getTimezone(), c.getNuTelefoneOperacional(), c.getDsEmailOperacional(),
                c.getDsEmailAlertas(),
                c.getWhatsappReconexaoAutomatica(), c.getWhatsappDelayMinSegundos(), c.getWhatsappDelayMaxSegundos(),
                c.getWhatsappSimularDigitando(), c.getWhatsappLimitePorMinuto(), c.getWhatsappLimitePorDia(),
                c.getWhatsappModoEnvio(), c.getTemplatesVersionamento(), c.getTemplatesExigirAprovacao(), c.getTemplatesValidarVariaveis(),
                c.getRetryAutomatico(), c.getRetryTentativas(), c.getRetryIntervaloSegundos(), c.getPrioridadePadrao(),
                c.getExpiracaoFilaHoras(), c.getAuditoriaHabilitada(),
                c.getWebhookInboundUrl(),
                c.getWebhookInboundHabilitado(),
                org.springframework.util.StringUtils.hasText(c.getWebhookInboundSecretEnc()),
                c.getDsGithubStatusDisparo(),
                c.getDsGithubFraseAtivacaoWhatsapp(),
                c.getWebhookRegistrarFilaSemDestinatario(),
                c.getDsGithubTemplateAssuntoWhatsapp(),
                c.getDsGithubTemplateMensagemWhatsapp(),
                c.getGithubNaoNotificarMovimentador(),
                c.getGithubNotificarStatusAlterado(),
                c.getGithubNotificarTarefaCriada(),
                c.getGithubNotificarResponsavelAlterado(),
                c.getGithubNotificarTarefaAtribuida(),
                c.getGithubIgnorarSemResponsavel(),
                c.getDsGithubDestinatariosModo(),
                c.getDsGithubDestinatariosExtras(),
                c.getGithubNotificarIssueFechadaReaberta(),
                c.getGithubNotificarIssueLabel(),
                c.getGithubNotificarSomenteCampoStatus(),
                c.getGithubPrAvisarAvaliadores(),
                c.getDsGithubPrStatusDisparo(),
                c.getDsGithubPrLoginsAvaliadores(),
                githubGraphqlTokenService.estaConfigurado(c),
                c.getNuGithubAppId(),
                c.getNuGithubInstallationId(),
                githubAppCredentialsService.privateKeyConfigurada(c),
                c.getDsGithubGraphqlUrl(),
                c.getDsGithubApiBaseUrl(),
                c.getNuGithubHttpConnectTimeoutMs(),
                c.getNuGithubHttpReadTimeoutMs(),
                c.getNuGithubInstallationTokenSkewSegundos(),
                c.getDtCriacao(), c.getDtAtualizacao());
    }
}
