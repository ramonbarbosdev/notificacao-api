package com.notificacao_api.dto.whatsapp;

import jakarta.validation.constraints.NotBlank;

public record OpcaoWhatsapp(
        @NotBlank String id,
        @NotBlank String titulo) {
}
