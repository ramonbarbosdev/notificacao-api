package com.notificacao_api.dto.configuracao;

import java.time.LocalDateTime;
import java.util.Set;

import com.notificacao_api.enums.ApiKeyScope;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

public record ApiKeyCreateRequest(
        @NotBlank String nome,
        @NotEmpty Set<ApiKeyScope> scopes,
        LocalDateTime expiraEm) {
}
