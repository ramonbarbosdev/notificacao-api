package com.notificacao_api.service.provedor;

public class ExcecaoEnvioProvedor extends RuntimeException {

    private final boolean reenviavel;

    public ExcecaoEnvioProvedor(String message, boolean reenviavel) {
        super(message);
        this.reenviavel = reenviavel;
    }

    public boolean isReenviavel() {
        return reenviavel;
    }
}
