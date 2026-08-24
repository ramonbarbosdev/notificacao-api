package com.notificacao_api.dto.whatsapp;

import java.time.LocalDateTime;

import com.notificacao_api.enums.WhatsappMensagemDirecao;
import com.notificacao_api.enums.WhatsappMensagemStatus;
import com.notificacao_api.enums.WhatsappMensagemTipo;

public record WhatsappMensagemResponse(
        Long idMensagem,
        String telefone,
        WhatsappMensagemDirecao direcao,
        WhatsappMensagemTipo tipo,
        String conteudo,
        WhatsappMensagemStatus status,
        String idExterno,
        LocalDateTime dtEnvio,
        LocalDateTime dtCriacao) {
}
