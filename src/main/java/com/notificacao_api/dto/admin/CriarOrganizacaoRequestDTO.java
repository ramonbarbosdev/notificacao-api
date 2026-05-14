package com.notificacao_api.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CriarOrganizacaoRequestDTO(
        @NotBlank String nmOrganizacao,
        @Size(max = 50) String dsDocumento) {
}
