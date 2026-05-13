package com.notificacao_api.dto.notification;

import com.notificacao_api.enums.CanalNotificacao;
import com.notificacao_api.enums.StatusNotificacao;

public record EnviarNotificacaoResposta(
        Boolean sucesso,
        Long idNotificacao,
        CanalNotificacao canal,
        StatusNotificacao status,
        String erro) {
}
