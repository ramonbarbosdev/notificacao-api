package com.notificacao_api.dto.admin;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CriarUsuarioOrganizacaoRequestDTO(
        @NotBlank @Pattern(regexp = "\\d{11}") String nuCpf,
        @NotBlank String nmUsuario,
        @Email String nmEmail,
        @NotBlank @Size(min = 6) String senha,
        @NotBlank @Pattern(regexp = "ADMIN|USER") String role) {
}
