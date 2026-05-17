package com.notificacao_api.dto.template;

import java.util.Map;

import jakarta.validation.constraints.NotNull;

public record TestarTemplateNotificacaoRequestDTO(
        @NotNull Map<String, String> variaveis) {
}
