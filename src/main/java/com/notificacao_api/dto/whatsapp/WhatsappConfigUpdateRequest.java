package com.notificacao_api.dto.whatsapp;

public record WhatsappConfigUpdateRequest(
        String phoneNumberId,
        String wabaId,
        String accessToken,
        String apiVersion,
        Boolean active) {
}
