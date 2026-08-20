package com.notificacao_api.dto.notificacao;

public record EnviarNotificacaoLoteItemResposta(
        int indice,
        String referenciaExterna,
        String destinatario,
        EnviarNotificacaoResposta resultado) {
}
