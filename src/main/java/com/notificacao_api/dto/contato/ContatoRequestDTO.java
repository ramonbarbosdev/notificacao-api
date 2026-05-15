package com.notificacao_api.dto.contato;

import com.notificacao_api.enums.CanalNotificacao;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ContatoRequestDTO(
        @NotNull CanalNotificacao canal,
        @NotBlank String destinatario,
        String motivo) {
}
