package com.notificacao_api.dto.whatsapp;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record WhatsappContatosGatewayResposta(
        Boolean sucesso,
        Long idOrganizacao,
        Integer total,
        List<WhatsappContatoGatewayItemDTO> contatos,
        String erro) {
}
