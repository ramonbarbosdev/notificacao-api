package com.notificacao_api.dto.whatsapp;

public record WhatsappInboundWebhookPayload(
        Long idOrganizacao,
        String telefone,
        String preview,
        String idMensagemExterna,
        String tipo,
        String nmContato,
        String recebidaEm) {
}
