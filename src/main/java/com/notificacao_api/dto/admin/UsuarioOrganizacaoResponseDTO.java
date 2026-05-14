package com.notificacao_api.dto.admin;

public record UsuarioOrganizacaoResponseDTO(
        Long idUsuario,
        String nuCpf,
        String nmUsuario,
        String nmEmail,
        Long idOrganizacao,
        String nmOrganizacao,
        String role,
        Boolean flAtivo) {
}
