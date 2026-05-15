package com.notificacao_api.dto.contato;

import java.time.LocalDateTime;

import com.notificacao_api.enums.CanalNotificacao;

public record ContatoResponseDTO(
        Long idContato,
        CanalNotificacao canal,
        String destinatario,
        Boolean consentimento,
        Boolean bloqueado,
        String motivoBloqueio,
        LocalDateTime dtConsentimento,
        LocalDateTime dtBloqueio) {
}
