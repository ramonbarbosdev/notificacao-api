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
public class TcTokenAudienciaMonitorAgendadoService {

    private static final Logger log = LoggerFactory.getLogger(TcTokenAudienciaMonitorAgendadoService.class);

    private final WhatsappSessionRepository whatsappSessionRepository;
    private final TcTokenAudienciaMonitorService monitorService;
    private final long pausaEntreOrgsMillis;

    public TcTokenAudienciaMonitorAgendadoService(
            WhatsappSessionRepository whatsappSessionRepository,
            TcTokenAudienciaMonitorService monitorService,
            @Value("${notificacao.tctoken.audiencia.pausa-entre-orgs-millis:30000}") long pausaEntreOrgsMillis) {
        this.whatsappSessionRepository = whatsappSessionRepository;
        this.monitorService = monitorService;
        this.pausaEntreOrgsMillis = Math.max(0, pausaEntreOrgsMillis);
    }

    @Scheduled(fixedDelayString = "${notificacao.tctoken.audiencia.intervalo-millis:21600000}")
    public void varreduraAgendada() {
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
                monitorService.atualizarCache(idOrganizacao);
            } catch (Exception ex) {
                log.warn("Falha na varredura agendada tctoken audiencia org={}: {}", idOrganizacao, ex.getMessage());
            }
        }
    }
}
