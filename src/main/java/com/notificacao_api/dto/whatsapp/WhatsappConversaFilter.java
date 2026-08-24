package com.notificacao_api.dto.whatsapp;

import com.notificacao_api.enums.WhatsappConversaOrigem;
import com.notificacao_api.enums.WhatsappConversaStatus;
import com.notificacao_api.enums.WhatsappMensagemDirecao;

public record WhatsappConversaFilter(
        String busca,
        Boolean prontoParaEnvioWhatsapp,
        WhatsappConversaStatus status,
        Boolean naoLida,
        WhatsappMensagemDirecao ultimaDirecaoMensagem,
        WhatsappConversaOrigem origem) {
}
