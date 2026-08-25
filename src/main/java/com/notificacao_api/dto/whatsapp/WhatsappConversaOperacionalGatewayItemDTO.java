package com.notificacao_api.dto.whatsapp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record WhatsappConversaOperacionalGatewayItemDTO(
        String telefone,
        String nmContato,
        String jid,
        Boolean prontoParaEnvio,
        Boolean possuiTcToken,
        Boolean inboundRecebida,
        String ultimaMensagem,
        String tipoUltimaMensagem,
        String dtUltimaMensagem,
        String ultimaDirecao) {
}
