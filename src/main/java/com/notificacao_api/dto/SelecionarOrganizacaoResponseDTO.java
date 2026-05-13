package com.notificacao_api.dto;

public record SelecionarOrganizacaoResponseDTO(
        String token,
        Long idOrganizacao,
        String role) {
}
