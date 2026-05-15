package com.notificacao_api.service;

import org.springframework.stereotype.Service;

import com.notificacao_api.dto.notificacao.EnviarNotificacaoRequisicao;
import com.notificacao_api.dto.notificacao.EnviarNotificacaoResposta;
import com.notificacao_api.dto.notificacao.FilaNotificacaoResponseDTO;
import com.notificacao_api.service.queue.FilaNotificacaoService;

@Service
public class NotificacaoService {

    private final FilaNotificacaoService filaService;

    public NotificacaoService(FilaNotificacaoService filaService) {
        this.filaService = filaService;
    }

    public EnviarNotificacaoResposta enviar(EnviarNotificacaoRequisicao requisicao) {
        return filaService.enfileirar(requisicao);
    }

    public FilaNotificacaoResponseDTO listarFila() {
        return filaService.listarFila();
    }
}
