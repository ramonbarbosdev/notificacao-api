package com.notificacao_api.dto.whatsapp;

import java.time.LocalDateTime;

public record WhatsappConfigResponse(
        String provider,
        String phoneNumberId,
        String wabaId,
        String apiVersion,
        boolean active,
        boolean accessTokenConfigured,
        LocalDateTime ultimoTeste) {
}
