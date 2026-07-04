package com.notificacao_api.service.queue;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.notificacao_api.config.PropriedadesProtecaoNotificacao;
import com.notificacao_api.enums.CanalNotificacao;
import com.notificacao_api.enums.StatusOperacionalSessao;
import com.notificacao_api.model.Notificacao;
import com.notificacao_api.model.WhatsappSession;
import com.notificacao_api.repository.WhatsappSessionRepository;
import com.notificacao_api.service.AlertaOperacionalService;

@Service
public class SegurancaOperacionalWhatsappService {

    private final WhatsappSessionRepository whatsappSessionRepository;
    private final PropriedadesProtecaoNotificacao propriedades;
    private final AlertaOperacionalService alertaOperacionalService;

    public SegurancaOperacionalWhatsappService(
            WhatsappSessionRepository whatsappSessionRepository,
            PropriedadesProtecaoNotificacao propriedades,
            AlertaOperacionalService alertaOperacionalService) {
        this.whatsappSessionRepository = whatsappSessionRepository;
        this.propriedades = propriedades;
        this.alertaOperacionalService = alertaOperacionalService;
    }

    @Transactional
    public void registrarSucesso(Notificacao notificacao, LocalDateTime proximoEnvioApos) {
        if (notificacao.getCanal() != CanalNotificacao.WHATSAPP) {
            return;
        }

        WhatsappSession sessao = whatsappSessionRepository.findByIdOrganizacao(notificacao.getIdOrganizacao())
                .orElse(null);
        if (sessao == null) {
            return;
        }

        sessao.setFalhasConsecutivas(0);
        sessao.setDtProximoEnvioApos(proximoEnvioApos);
        if (sessao.getStatusOperacional() == StatusOperacionalSessao.PAUSADA
                && (sessao.getDtPausadoAte() == null || !sessao.getDtPausadoAte().isAfter(LocalDateTime.now()))) {
            sessao.setStatusOperacional(StatusOperacionalSessao.ATIVA);
            sessao.setDtPausadoAte(null);
        }
        whatsappSessionRepository.save(sessao);
    }

    @Transactional
    public void registrarFalha(Notificacao notificacao, String ultimoErro) {
        if (notificacao.getCanal() != CanalNotificacao.WHATSAPP) {
            return;
        }

        WhatsappSession sessao = whatsappSessionRepository.findByIdOrganizacao(notificacao.getIdOrganizacao())
                .orElse(null);
        if (sessao == null) {
            return;
        }

        int falhas = sessao.getFalhasConsecutivas() == null ? 1 : sessao.getFalhasConsecutivas() + 1;
        sessao.setFalhasConsecutivas(falhas);

        boolean riscoBanimento = falhas >= propriedades.maximoFalhasConsecutivas();
        if (riscoBanimento) {
            sessao.setStatusOperacional(StatusOperacionalSessao.RISCO_BANIMENTO);
            sessao.setDtPausadoAte(LocalDateTime.now().plusSeconds(propriedades.pausaAutomaticaSegundos()));
        } else {
            sessao.setStatusOperacional(StatusOperacionalSessao.PAUSADA);
            sessao.setDtPausadoAte(LocalDateTime.now().plusSeconds(propriedades.pausaAutomaticaSegundos()));
        }

        whatsappSessionRepository.save(sessao);

        try {
            alertaOperacionalService.registrarPausaWhatsappAposFalha(notificacao, ultimoErro, riscoBanimento);
        } catch (Exception ex) {
            // nao interrompe o fluxo da fila
        }
    }
}
