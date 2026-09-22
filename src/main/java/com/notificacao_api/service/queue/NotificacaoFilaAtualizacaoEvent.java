package com.notificacao_api.service.queue;

import com.notificacao_api.enums.StatusNotificacao;

public record NotificacaoFilaAtualizacaoEvent(
        Long idOrganizacao,
        Long idNotificacao,
        StatusNotificacao status,
        String erro,
        String motivoAguardando,
        String codigoErro) {
}
