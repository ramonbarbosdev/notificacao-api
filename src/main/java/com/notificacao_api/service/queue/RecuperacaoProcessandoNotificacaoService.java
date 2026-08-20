package com.notificacao_api.service.queue;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.notificacao_api.enums.CodigoErroEnvio;
import com.notificacao_api.enums.StatusNotificacao;
import com.notificacao_api.model.Notificacao;
import com.notificacao_api.repository.NotificacaoRepository;

@Component
public class RecuperacaoProcessandoNotificacaoService {

    private final NotificacaoRepository notificacaoRepository;
    private final ProtecaoNotificacaoService protecaoService;
    private final FilaNotificacaoService filaNotificacaoService;

    public RecuperacaoProcessandoNotificacaoService(
            NotificacaoRepository notificacaoRepository,
            ProtecaoNotificacaoService protecaoService,
            FilaNotificacaoService filaNotificacaoService) {
        this.notificacaoRepository = notificacaoRepository;
        this.protecaoService = protecaoService;
        this.filaNotificacaoService = filaNotificacaoService;
    }

    @Scheduled(fixedDelayString = "${notificacao.protecao.recuperacao-processando-millis:300000}")
    @Transactional
    public void recuperarProcessandoOrfaos() {
        LocalDateTime limite = protecaoService.agora().minusMinutes(
                protecaoService.propriedades().recuperacaoProcessandoMinutos());

        List<Notificacao> orfaos = notificacaoRepository
                .findByStatusAndDtUltimoProcessamentoBefore(StatusNotificacao.PROCESSANDO, limite);

        for (Notificacao notificacao : orfaos) {
            notificacao.setStatus(StatusNotificacao.PENDENTE);
            notificacao.setMotivoAguardando(
                    "Processamento interrompido. Reenfileirado automaticamente.");
            notificacao.setCodigoErro(CodigoErroEnvio.AGUARDANDO_PROTECAO.name());
            notificacao.setErro(null);
            notificacao.setDtProximaTentativa(protecaoService.agora());
            notificacaoRepository.save(notificacao);
            filaNotificacaoService.notificarAtualizacaoFilaPublica(notificacao);
        }
    }
}
