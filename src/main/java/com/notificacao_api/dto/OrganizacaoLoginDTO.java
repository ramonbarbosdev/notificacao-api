package com.notificacao_api.dto;

public record OrganizacaoLoginDTO(
        Long idOrganizacao,
        String nmOrganizacao,
        String role) {
}
