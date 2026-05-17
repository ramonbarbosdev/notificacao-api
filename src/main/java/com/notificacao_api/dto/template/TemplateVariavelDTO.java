package com.notificacao_api.dto.template;

import com.notificacao_api.enums.TipoVariavelTemplate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record TemplateVariavelDTO(
        @NotBlank String chave,
        String label,
        @NotNull TipoVariavelTemplate tipo,
        Boolean obrigatoria,
        String exemplo) {
}
