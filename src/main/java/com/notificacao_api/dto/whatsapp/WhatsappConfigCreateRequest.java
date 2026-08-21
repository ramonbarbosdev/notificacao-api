package com.notificacao_api.dto.whatsapp;

import jakarta.validation.constraints.NotBlank;

public record WhatsappConfigCreateRequest(
        @NotBlank String phoneNumberId,
        String wabaId,
        @NotBlank String accessToken,
        String apiVersion,
        Boolean active) {
}
