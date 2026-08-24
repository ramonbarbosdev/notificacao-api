package com.notificacao_api.dto.whatsapp;

import java.util.List;

public record WhatsappMensagensGatewayResposta(
        Boolean sucesso,
        Long idOrganizacao,
        String telefone,
        Integer total,
        List<WhatsappMensagemSessaoItemDTO> mensagens,
        String erro) {
}
