package com.notificacao_api.dto.configuracao;

import java.time.LocalDateTime;
import java.util.Set;

import com.notificacao_api.enums.ApiKeyScope;

public record ApiKeyCreatedResponse(
        Long idApiKey,
        String nome,
        String prefixo,
        String chave,
        Set<ApiKeyScope> scopes,
        LocalDateTime expiraEm,
        LocalDateTime dtCriacao) {
}
