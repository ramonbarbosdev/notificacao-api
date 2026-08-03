package com.notificacao_api.service.queue;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.notificacao_api.config.PropriedadesProtecaoNotificacao;
import com.notificacao_api.model.OrganizacaoConfiguracao;
import com.notificacao_api.repository.OrganizacaoConfiguracaoRepository;

@Service
public class ProtecaoOperacionalConfigResolver {

    private final OrganizacaoConfiguracaoRepository organizacaoConfiguracaoRepository;
    private final PropriedadesProtecaoNotificacao propriedades;

    public ProtecaoOperacionalConfigResolver(
            OrganizacaoConfiguracaoRepository organizacaoConfiguracaoRepository,
            PropriedadesProtecaoNotificacao propriedades) {
        this.organizacaoConfiguracaoRepository = organizacaoConfiguracaoRepository;
        this.propriedades = propriedades;
    }

    @Transactional(readOnly = true)
    public int limiteFalhasSessao(Long idOrganizacao) {
        return organizacaoConfiguracaoRepository.findByIdOrganizacao(idOrganizacao)
                .map(OrganizacaoConfiguracao::getLimiteFalhasParaBloqueio)
                .filter(limite -> limite != null && limite > 0)
                .orElse(propriedades.maximoFalhasConsecutivas());
    }

    @Transactional(readOnly = true)
    public int limiteFalhasBloqueioContato(Long idOrganizacao) {
        return limiteFalhasSessao(idOrganizacao);
    }

    @Transactional(readOnly = true)
    public boolean bloqueioAutomaticoContatoHabilitado(Long idOrganizacao) {
        return organizacaoConfiguracaoRepository.findByIdOrganizacao(idOrganizacao)
                .map(OrganizacaoConfiguracao::getBloqueioAutomatico)
                .orElse(true);
    }

    @Transactional(readOnly = true)
    public int tentativasMaximasNotificacao(Long idOrganizacao) {
        return organizacaoConfiguracaoRepository.findByIdOrganizacao(idOrganizacao)
                .filter(config -> Boolean.TRUE.equals(config.getRetryAutomatico()))
                .map(OrganizacaoConfiguracao::getRetryTentativas)
                .filter(tentativas -> tentativas != null && tentativas > 0)
                .orElse(propriedades.maximoTentativas());
    }

    public long pausaAutomaticaSegundos() {
        return propriedades.pausaAutomaticaSegundos();
    }

    public long pausaRiscoSegundos() {
        return propriedades.pausaRiscoSegundos();
    }

    public long decaimentoFalhasMinutos() {
        return propriedades.decaimentoFalhasMinutos();
    }

    public int limiteBloqueioSessaoMultiplicador() {
        return propriedades.limiteBloqueioSessaoMultiplicador();
    }
}
