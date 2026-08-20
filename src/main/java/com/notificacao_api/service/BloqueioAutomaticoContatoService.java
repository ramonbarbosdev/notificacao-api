package com.notificacao_api.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.notificacao_api.enums.CanalNotificacao;
import com.notificacao_api.enums.StatusNotificacao;
import com.notificacao_api.model.Contato;
import com.notificacao_api.model.Notificacao;
import com.notificacao_api.model.Organizacao;
import com.notificacao_api.repository.ContatoRepository;
import com.notificacao_api.repository.NotificacaoRepository;
import com.notificacao_api.repository.OrganizacaoRepository;
import com.notificacao_api.service.queue.ClassificacaoErroEnvio;
import com.notificacao_api.service.queue.ProtecaoOperacionalConfigResolver;

@Service
public class BloqueioAutomaticoContatoService {

    private final ContatoRepository contatoRepository;
    private final NotificacaoRepository notificacaoRepository;
    private final OrganizacaoRepository organizacaoRepository;
    private final ProtecaoOperacionalConfigResolver configResolver;
    private final AlertaOperacionalService alertaOperacionalService;

    public BloqueioAutomaticoContatoService(
            ContatoRepository contatoRepository,
            NotificacaoRepository notificacaoRepository,
            OrganizacaoRepository organizacaoRepository,
            ProtecaoOperacionalConfigResolver configResolver,
            AlertaOperacionalService alertaOperacionalService) {
        this.contatoRepository = contatoRepository;
        this.notificacaoRepository = notificacaoRepository;
        this.organizacaoRepository = organizacaoRepository;
        this.configResolver = configResolver;
        this.alertaOperacionalService = alertaOperacionalService;
    }

    @Transactional
    public void avaliarAposFalhaDefinitiva(
            Notificacao notificacao,
            String erro,
            ClassificacaoErroEnvio classificacao) {
        if (!configResolver.bloqueioAutomaticoContatoHabilitado(notificacao.getIdOrganizacao())) {
            return;
        }

        if (classificacao.restricaoContatoWhatsapp()) {
            return;
        }

        int limite = configResolver.limiteFalhasBloqueioContato(notificacao.getIdOrganizacao());
        long falhasDestinatario = notificacaoRepository.countByIdOrganizacaoAndCanalAndDestinatarioAndStatus(
                notificacao.getIdOrganizacao(),
                notificacao.getCanal(),
                notificacao.getDestinatario(),
                StatusNotificacao.FALHOU);

        boolean deveBloquear = classificacao.bloqueioContatoImediato()
                || falhasDestinatario >= limite;

        if (!deveBloquear) {
            return;
        }

        Contato contato = contatoRepository
                .findByOrganizacao_IdOrganizacaoAndCanalAndDestinatario(
                        notificacao.getIdOrganizacao(),
                        notificacao.getCanal(),
                        notificacao.getDestinatario())
                .orElseGet(() -> novoContato(notificacao));

        if (Boolean.TRUE.equals(contato.getBloqueado())) {
            return;
        }

        String motivo = classificacao.bloqueioContatoImediato()
                ? "Bloqueio automatico: destinatario invalido ou indisponivel no canal. " + resumirErro(erro)
                : "Bloqueio automatico apos " + falhasDestinatario + " falha(s) definitiva(s) de envio. "
                        + resumirErro(erro);

        contato.setBloqueado(true);
        contato.setMotivoBloqueio(motivo);
        contato.setDtBloqueio(LocalDateTime.now());
        contatoRepository.save(contato);

        try {
            alertaOperacionalService.registrarBloqueioAutomaticoContato(notificacao, motivo);
        } catch (Exception ex) {
            // nao interrompe o fluxo da fila
        }
    }

    private Contato novoContato(Notificacao notificacao) {
        Organizacao organizacao = organizacaoRepository.getReferenceById(notificacao.getIdOrganizacao());
        Contato contato = new Contato();
        contato.setOrganizacao(organizacao);
        contato.setCanal(notificacao.getCanal());
        contato.setDestinatario(notificacao.getDestinatario());
        contato.setNmContato(notificacao.getDestinatario());
        contato.setConsentimento(false);
        return contato;
    }

    private String resumirErro(String erro) {
        if (erro == null || erro.isBlank()) {
            return "Erro nao informado.";
        }
        return erro.length() > 250 ? erro.substring(0, 250) + "..." : erro;
    }
}
