package com.notificacao_api.service.queue;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.annotation.Transactional;

@Component
public class NotificacaoFilaAtualizacaoListener {

    private final FilaNotificacaoService filaNotificacaoService;
    private final NotificacaoFilaWebSocketService notificacaoFilaWebSocketService;

    public NotificacaoFilaAtualizacaoListener(
            FilaNotificacaoService filaNotificacaoService,
            NotificacaoFilaWebSocketService notificacaoFilaWebSocketService) {
        this.filaNotificacaoService = filaNotificacaoService;
        this.notificacaoFilaWebSocketService = notificacaoFilaWebSocketService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(readOnly = true)
    public void publicarWebSocketAposCommit(NotificacaoFilaAtualizacaoEvent event) {
        if (event.idOrganizacao() == null) {
            return;
        }

        var resumo = filaNotificacaoService.resumoFilaOrganizacao(event.idOrganizacao());
        notificacaoFilaWebSocketService.publicarAtualizacao(
                event.idOrganizacao(),
                event.idNotificacao(),
                event.status(),
                event.erro(),
                event.motivoAguardando(),
                event.codigoErro(),
                resumo);
    }
}
