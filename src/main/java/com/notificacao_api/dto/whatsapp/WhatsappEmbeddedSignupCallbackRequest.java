package com.notificacao_api.dto.whatsapp;

import jakarta.validation.constraints.NotBlank;

public record WhatsappEmbeddedSignupCallbackRequest(
        @NotBlank String code,
        String phoneNumberId,
        String wabaId,
        String apiVersion) {
}
