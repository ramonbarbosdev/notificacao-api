package com.notificacao_api.service.whatsapp.provider;

import com.notificacao_api.enums.WhatsappMensagemStatus;

public record ResultadoEnvioWhatsapp(
        String externalMessageId,
        boolean confirmado,
        WhatsappMensagemStatus status,
        String erro) {

    public static ResultadoEnvioWhatsapp confirmado(String externalMessageId) {
        return new ResultadoEnvioWhatsapp(externalMessageId, true, WhatsappMensagemStatus.SENT, null);
    }

    public static ResultadoEnvioWhatsapp falha(String erro) {
        return new ResultadoEnvioWhatsapp(null, false, WhatsappMensagemStatus.FAILED, erro);
    }
}
