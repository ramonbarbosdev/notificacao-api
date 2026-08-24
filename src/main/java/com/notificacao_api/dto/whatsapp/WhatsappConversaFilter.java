package com.notificacao_api.dto.whatsapp;

import com.notificacao_api.enums.WhatsappConversaStatus;

public record WhatsappConversaFilter(
        String busca,
        Boolean prontoParaEnvioWhatsapp,
        WhatsappConversaStatus status,
        Boolean naoLida) {
}
