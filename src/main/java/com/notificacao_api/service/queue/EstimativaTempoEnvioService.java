package com.notificacao_api.service.queue;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;

import com.notificacao_api.config.PropriedadesProtecaoNotificacao;
import com.notificacao_api.dto.notificacao.EstimativaEnvioNotificacao;
import com.notificacao_api.enums.CanalNotificacao;
import com.notificacao_api.enums.StatusNotificacao;
import com.notificacao_api.model.Notificacao;
import com.notificacao_api.model.WhatsappSession;
import com.notificacao_api.repository.NotificacaoRepository;
import com.notificacao_api.repository.WhatsappSessionRepository;

@Service
public class EstimativaTempoEnvioService {

    private final NotificacaoRepository notificacaoRepository;
    private final WhatsappSessionRepository whatsappSessionRepository;
    private final ProtecaoNotificacaoService protecaoService;
    private final PropriedadesProtecaoNotificacao propriedades;

    public EstimativaTempoEnvioService(
            NotificacaoRepository notificacaoRepository,
            WhatsappSessionRepository whatsappSessionRepository,
            ProtecaoNotificacaoService protecaoService,
            PropriedadesProtecaoNotificacao propriedades) {
        this.notificacaoRepository = notificacaoRepository;
        this.whatsappSessionRepository = whatsappSessionRepository;
        this.protecaoService = protecaoService;
        this.propriedades = propriedades;
    }

    public EstimativaEnvioNotificacao calcular(Notificacao notificacao) {
        if (notificacao == null
                || notificacao.getStatus() == StatusNotificacao.BLOQUEADA
                || notificacao.getIdNotificacao() == null) {
            return EstimativaEnvioNotificacao.indisponivel();
        }

        long naFila = notificacaoRepository.countByIdOrganizacaoAndCanalAndStatusIn(
                notificacao.getIdOrganizacao(),
                notificacao.getCanal(),
                List.of(StatusNotificacao.PENDENTE, StatusNotificacao.PROCESSANDO));

        int posicaoFila = (int) Math.max(0, naFila - 1);
        long segundos = calcularSegundos(notificacao, posicaoFila);

        return new EstimativaEnvioNotificacao(
                segundos,
                posicaoFila,
                formatarTempo(segundos));
    }

    long calcularSegundos(Notificacao notificacao, int posicaoFila) {
        double delayMedio = (propriedades.delayMinimoSegundos() + propriedades.delayMaximoSegundos()) / 2.0;
        double cicloAgendador = propriedades.intervaloAgendadorMillis() / 1000.0;
        int lote = Math.max(1, propriedades.tamanhoLoteAgendador());
        double tempoPorMensagem = delayMedio + (cicloAgendador / lote);

        long estimativa = (long) Math.ceil(posicaoFila * tempoPorMensagem + delayMedio * 0.5);

        if (notificacao.getCanal() == CanalNotificacao.WHATSAPP) {
            estimativa = Math.max(estimativa, calcularEsperaWhatsapp(notificacao.getIdOrganizacao(), posicaoFila, tempoPorMensagem));
        }

        return Math.max(5L, estimativa);
    }

    private long calcularEsperaWhatsapp(Long idOrganizacao, int posicaoFila, double tempoPorMensagem) {
        LocalDateTime agora = protecaoService.agora();
        WhatsappSession sessao = whatsappSessionRepository.findByIdOrganizacao(idOrganizacao).orElse(null);

        if (sessao == null || sessao.getDtProximoEnvioApos() == null || !sessao.getDtProximoEnvioApos().isAfter(agora)) {
            return 0L;
        }

        long esperaSessao = Duration.between(agora, sessao.getDtProximoEnvioApos()).getSeconds();
        return esperaSessao + (long) Math.ceil(posicaoFila * tempoPorMensagem);
    }

    static String formatarTempo(long segundos) {
        if (segundos <= 5) {
            return "em alguns segundos";
        }
        if (segundos < 60) {
            return "cerca de " + segundos + " segundos";
        }

        long minutos = Math.round(segundos / 60.0);
        if (minutos < 60) {
            return "cerca de " + minutos + (minutos == 1 ? " minuto" : " minutos");
        }

        long horas = Math.round(segundos / 3600.0);
        return "cerca de " + horas + (horas == 1 ? " hora" : " horas");
    }
}
