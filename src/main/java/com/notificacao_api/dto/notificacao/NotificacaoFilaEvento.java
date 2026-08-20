package com.notificacao_api.dto.notificacao;

import java.time.LocalDateTime;

import com.notificacao_api.enums.StatusNotificacao;

public record NotificacaoFilaEvento(
        Long idOrganizacao,
        String tipo,
        Long idNotificacao,
        StatusNotificacao status,
        String erro,
        String motivoAguardando,
        String codigoErro,
        FilaResumoResponseDTO resumo,
        LocalDateTime dtEvento) {

    public static final String TIPO_FILA_ATUALIZADA = "FILA_ATUALIZADA";

    public static NotificacaoFilaEvento atualizada(
            Long idOrganizacao,
            Long idNotificacao,
            StatusNotificacao status,
            String erro,
            String motivoAguardando,
            String codigoErro,
            FilaResumoResponseDTO resumo) {
        return new NotificacaoFilaEvento(
                idOrganizacao,
                TIPO_FILA_ATUALIZADA,
                idNotificacao,
                status,
                erro,
                motivoAguardando,
                codigoErro,
                resumo,
                LocalDateTime.now());
    }
}
