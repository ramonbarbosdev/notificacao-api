package com.notificacao_api.dto.whatsapp;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record WhatsappConversasOperacionaisGatewayResposta(
        Boolean sucesso,
        Long idOrganizacao,
        Integer total,
        Integer prontas,
        List<WhatsappConversaOperacionalGatewayItemDTO> conversas,
        String erro) {
}
