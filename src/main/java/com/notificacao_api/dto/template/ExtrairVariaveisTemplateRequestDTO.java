package com.notificacao_api.dto.template;

import jakarta.validation.constraints.NotBlank;

public record ExtrairVariaveisTemplateRequestDTO(
        @NotBlank String conteudo) {
}
