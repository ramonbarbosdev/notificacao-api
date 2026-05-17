package com.notificacao_api.dto.template;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

public record ValidarTemplateRequestDTO(
        @NotBlank String conteudo,
        @Valid List<TemplateVariavelDTO> variaveis) {
}
