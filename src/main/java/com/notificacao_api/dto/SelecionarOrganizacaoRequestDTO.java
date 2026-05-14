package com.notificacao_api.dto;

import jakarta.validation.constraints.NotNull;

public record SelecionarOrganizacaoRequestDTO(
    @NotNull Long idOrganizacao
) {
}
