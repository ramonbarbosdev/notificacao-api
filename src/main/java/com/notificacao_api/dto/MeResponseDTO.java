package com.notificacao_api.dto;

public record MeResponseDTO(
        Long idUsuario,
        String tipoGlobal,
        Long idOrganizacao,
        String role,
        String nmUsuario,
        String nmEmail) {
}
