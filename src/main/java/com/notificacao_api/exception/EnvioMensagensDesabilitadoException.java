package com.notificacao_api.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class EnvioMensagensDesabilitadoException extends ResponseStatusException {

    public static final String CODIGO = "ENVIO_MENSAGENS_DESABILITADO";
    public static final String MENSAGEM =
            "Envio de mensagens desabilitado para esta organizacao. Reative em Configuracoes para voltar a enviar.";

    public EnvioMensagensDesabilitadoException() {
        super(HttpStatus.CONFLICT, MENSAGEM);
    }
}
