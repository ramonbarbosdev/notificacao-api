package com.notificacao_api.dto.whatsapp;

public record TcTokenAudienciaKpisResponse(
        int total,
        int proximoExpirar,
        int ausente,
        int expirados,
        int ok) {
}
