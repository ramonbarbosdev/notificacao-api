package com.notificacao_api.dto.whatsapp;

import java.util.List;

public record WhatsappHistoricoCarregarMaisGatewayResposta(
        Boolean sucesso,
        String idOrganizacao,
        String telefone,
        Integer importadas,
        Boolean fimHistorico,
        List<WhatsappMensagemSessaoItemDTO> mensagens,
        String erro) {
}
