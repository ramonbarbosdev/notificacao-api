package com.notificacao_api.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.notificacao_api.dto.configuracao.OrganizacaoConfiguracaoRequest;
import com.notificacao_api.dto.configuracao.OrganizacaoConfiguracaoResponse;
import com.notificacao_api.model.OrganizacaoConfiguracao;
import com.notificacao_api.repository.OrganizacaoConfiguracaoRepository;

@Service
public class OrganizacaoConfiguracaoService {

    private final OrganizacaoConfiguracaoRepository repository;
    private final TenantContextService tenantContextService;
    private final AuditoriaEventoService auditoriaService;

    public OrganizacaoConfiguracaoService(
            OrganizacaoConfiguracaoRepository repository,
            TenantContextService tenantContextService,
            AuditoriaEventoService auditoriaService) {
        this.repository = repository;
        this.tenantContextService = tenantContextService;
        this.auditoriaService = auditoriaService;
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

    @Transactional(readOnly = true)
    public boolean exigeConsentimento(Long idOrganizacao) {
        return repository.findByIdOrganizacao(idOrganizacao)
                .map(OrganizacaoConfiguracao::getExigirConsentimento)
                .orElse(true);
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
        if (r.exigirConsentimento() != null) c.setExigirConsentimento(r.exigirConsentimento());
        if (r.consentimentoExpira() != null) c.setConsentimentoExpira(r.consentimentoExpira());
        c.setDiasExpiracaoConsentimento(r.diasExpiracaoConsentimento());
        if (r.bloqueioAutomatico() != null) c.setBloqueioAutomatico(r.bloqueioAutomatico());
        if (r.limiteFalhasParaBloqueio() != null) c.setLimiteFalhasParaBloqueio(r.limiteFalhasParaBloqueio());
        if (r.templatesVersionamento() != null) c.setTemplatesVersionamento(r.templatesVersionamento());
        if (r.templatesExigirAprovacao() != null) c.setTemplatesExigirAprovacao(r.templatesExigirAprovacao());
        if (r.templatesValidarVariaveis() != null) c.setTemplatesValidarVariaveis(r.templatesValidarVariaveis());
        if (r.retryAutomatico() != null) c.setRetryAutomatico(r.retryAutomatico());
        if (r.retryTentativas() != null) c.setRetryTentativas(r.retryTentativas());
        if (r.retryIntervaloSegundos() != null) c.setRetryIntervaloSegundos(r.retryIntervaloSegundos());
        if (r.prioridadePadrao() != null) c.setPrioridadePadrao(r.prioridadePadrao());
        if (r.expiracaoFilaHoras() != null) c.setExpiracaoFilaHoras(r.expiracaoFilaHoras());
        if (r.auditoriaHabilitada() != null) c.setAuditoriaHabilitada(r.auditoriaHabilitada());
    }

    private OrganizacaoConfiguracaoResponse toResponse(OrganizacaoConfiguracao c) {
        return new OrganizacaoConfiguracaoResponse(
                c.getIdOrganizacaoConfiguracao(), c.getIdOrganizacao(), c.getNmExibicao(), c.getDsLogoUrl(),
                c.getDsIdioma(), c.getTimezone(), c.getNuTelefoneOperacional(), c.getDsEmailOperacional(),
                c.getDsEmailAlertas(),
                c.getWhatsappReconexaoAutomatica(), c.getWhatsappDelayMinSegundos(), c.getWhatsappDelayMaxSegundos(),
                c.getWhatsappSimularDigitando(), c.getWhatsappLimitePorMinuto(), c.getWhatsappLimitePorDia(),
                c.getWhatsappModoEnvio(), c.getExigirConsentimento(), c.getConsentimentoExpira(),
                c.getDiasExpiracaoConsentimento(), c.getBloqueioAutomatico(), c.getLimiteFalhasParaBloqueio(),
                c.getTemplatesVersionamento(), c.getTemplatesExigirAprovacao(), c.getTemplatesValidarVariaveis(),
                c.getRetryAutomatico(), c.getRetryTentativas(), c.getRetryIntervaloSegundos(), c.getPrioridadePadrao(),
                c.getExpiracaoFilaHoras(), c.getAuditoriaHabilitada(), c.getDtCriacao(), c.getDtAtualizacao());
    }
}
