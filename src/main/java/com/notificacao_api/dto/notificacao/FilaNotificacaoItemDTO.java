package com.notificacao_api.dto.notificacao;

import java.time.LocalDateTime;

import com.notificacao_api.enums.CanalNotificacao;
import com.notificacao_api.enums.StatusNotificacao;

public record FilaNotificacaoItemDTO(
        Long idNotificacao,
        CanalNotificacao canal,
        String destinatario,
        StatusNotificacao status,
        String provider,
        Integer tentativas,
        LocalDateTime proximaTentativa,
        String erro,
        LocalDateTime criadoEm) {
}
