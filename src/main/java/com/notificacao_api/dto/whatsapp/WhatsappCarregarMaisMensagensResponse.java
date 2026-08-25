package com.notificacao_api.dto.whatsapp;

import java.util.List;

public record WhatsappCarregarMaisMensagensResponse(
        List<WhatsappMensagemResponse> mensagens,
        boolean fimHistorico,
        int importadas) {
}
