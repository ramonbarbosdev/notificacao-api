package com.notificacao_api.dto.whatsapp;

public record WhatsappMensagemSessaoItemDTO(
        String direcao,
        String tipo,
        String preview,
        String idMensagemExterna,
        String enviadaEm) {
}
