package com.notificacao_api.dto.admin;

public record OrganizacaoResponseDTO(
        Long idOrganizacao,
        String nmOrganizacao,
        String dsDocumento,
        Boolean flAtivo) {
}
