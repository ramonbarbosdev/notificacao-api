package com.notificacao_api.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class WhatsappNaoConectadoException extends ResponseStatusException {

    public static final String CODIGO = "WHATSAPP_NAO_CONECTADO";
    public static final String MENSAGEM =
            "WhatsApp nao conectado para esta organizacao. Conecte o numero em Integracoes antes de enviar mensagens.";

    public WhatsappNaoConectadoException() {
        super(HttpStatus.CONFLICT, MENSAGEM);
    }
}
