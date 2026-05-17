package com.notificacao_api.dto;

public record ApiErrorResponse(
        int status,
        String mensagem,
        String erro,
        String path
) {}