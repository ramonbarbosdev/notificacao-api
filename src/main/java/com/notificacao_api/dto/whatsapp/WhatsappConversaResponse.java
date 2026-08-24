package com.notificacao_api.dto.whatsapp;

import java.time.LocalDateTime;

import com.notificacao_api.enums.WhatsappConversaStatus;

public record WhatsappConversaResponse(
        Long idConversa,
        Long idContato,
        String telefone,
        String nmContato,
        String ultimaMensagem,
        String tipoUltimaMensagem,
        WhatsappConversaStatus status,
        Boolean naoLida,
        LocalDateTime dtUltimaMensagem,
        Boolean exigirConsentimento) {
}
