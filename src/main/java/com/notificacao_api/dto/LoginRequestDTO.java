package com.notificacao_api.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequestDTO(
        @NotBlank String nuCpf,
        @NotBlank String dsSenha) {
}
