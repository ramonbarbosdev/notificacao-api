package com.notificacao_api.dto.whatsapp;

import java.time.LocalDateTime;

import com.notificacao_api.enums.WhatsappConversaOrigem;
import com.notificacao_api.enums.WhatsappConversaStatus;
import com.notificacao_api.enums.WhatsappMensagemDirecao;

public record WhatsappConversaResponse(
        Long idConversa,
        Long idContato,
        String telefone,
        String nmContato,
        String ultimaMensagem,
        String tipoUltimaMensagem,
        WhatsappMensagemDirecao ultimaDirecaoMensagem,
        WhatsappConversaOrigem origem,
        Boolean registradaNaApi,
        Boolean visivelNaSessaoGateway,
        WhatsappConversaStatus status,
        Boolean naoLida,
        LocalDateTime dtUltimaMensagem,
        Boolean exigirConsentimento,
        Boolean prontoParaEnvioWhatsapp,
        Boolean inboundRecebidaWhatsapp) {
}
