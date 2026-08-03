package com.notificacao_api.service.queue;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import com.notificacao_api.dto.notificacao.FilaResumoResponseDTO;
import com.notificacao_api.dto.notificacao.NotificacaoFilaEvento;
import com.notificacao_api.enums.StatusNotificacao;

@Service
public class NotificacaoFilaWebSocketService {

    private static final String TOPICO_ORGANIZACAO = "/topic/notificacoes/organizacao/";

    private final SimpMessagingTemplate messagingTemplate;

    public NotificacaoFilaWebSocketService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void publicarAtualizacao(
            Long idOrganizacao,
            Long idNotificacao,
            StatusNotificacao status,
            FilaResumoResponseDTO resumo) {
        if (idOrganizacao == null) {
            return;
        }

        try {
            messagingTemplate.convertAndSend(
                    TOPICO_ORGANIZACAO + idOrganizacao,
                    NotificacaoFilaEvento.atualizada(idOrganizacao, idNotificacao, status, resumo));
        } catch (Exception ex) {
            // nao interrompe o fluxo da fila
        }
    }
}
