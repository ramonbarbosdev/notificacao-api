package com.notificacao_api.dto.template;

import java.util.Map;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record EnviarTemplateNotificacaoRequestDTO(
        @NotBlank String templateKey,
        @NotBlank String destinatario,
        @NotNull Map<String, String> variaveis) {
}
