package com.notificacao_api.dto.configuracao;

import java.time.LocalDateTime;
import java.util.Set;

import com.notificacao_api.enums.ApiKeyScope;

public record ApiKeyResponse(
        Long idApiKey,
        String nome,
        String prefixo,
        Set<ApiKeyScope> scopes,
        Boolean ativo,
        LocalDateTime ultimoUsoEm,
        LocalDateTime expiraEm,
        LocalDateTime dtCriacao,
        LocalDateTime dtRevogacao) {
}
