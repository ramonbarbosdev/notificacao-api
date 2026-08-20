package com.notificacao_api.dto.whatsapp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record WhatsappContatoGatewayItemDTO(
        String telefone,
        String nmContato,
        String jid) {
}
