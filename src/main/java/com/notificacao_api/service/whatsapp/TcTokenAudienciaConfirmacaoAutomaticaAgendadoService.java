package com.notificacao_api.service.whatsapp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.notificacao_api.enums.WhatsappSessionStatus;
import com.notificacao_api.model.WhatsappSession;
import com.notificacao_api.repository.WhatsappSessionRepository;

@Service
public class TcTokenAudienciaConfirmacaoAutomaticaAgendadoService {

    private static final Logger log = LoggerFactory.getLogger(TcTokenAudienciaConfirmacaoAutomaticaAgendadoService.class);

    private final WhatsappSessionRepository whatsappSessionRepository;
    private final TcTokenAudienciaConfirmacaoAutomaticaService confirmacaoAutomaticaService;
    private final long pausaEntreOrgsMillis;

    public TcTokenAudienciaConfirmacaoAutomaticaAgendadoService(
            WhatsappSessionRepository whatsappSessionRepository,
            TcTokenAudienciaConfirmacaoAutomaticaService confirmacaoAutomaticaService,
            @Value("${notificacao.tctoken.audiencia.pausa-entre-orgs-millis:30000}") long pausaEntreOrgsMillis) {
        this.whatsappSessionRepository = whatsappSessionRepository;
        this.confirmacaoAutomaticaService = confirmacaoAutomaticaService;
        this.pausaEntreOrgsMillis = Math.max(0, pausaEntreOrgsMillis);
    }

    @Scheduled(fixedDelayString = "${notificacao.tctoken.confirmacao-automatica.intervalo-millis:86400000}")
    public void executarConfirmacaoAutomatica() {
        boolean primeira = true;
        for (WhatsappSession sessao : whatsappSessionRepository.findAll()) {
            if (sessao.getTpStatus() != WhatsappSessionStatus.CONECTADO) {
                continue;
            }
            if (!primeira && pausaEntreOrgsMillis > 0) {
                try {
                    Thread.sleep(pausaEntreOrgsMillis);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            primeira = false;

            Long idOrganizacao = sessao.getIdOrganizacao();
            try {
                confirmacaoAutomaticaService.processarOrganizacao(idOrganizacao);
            } catch (Exception ex) {
                log.warn("Falha job confirmacao automatica tctoken org={}: {}", idOrganizacao, ex.getMessage());
            }
        }
    }
}
