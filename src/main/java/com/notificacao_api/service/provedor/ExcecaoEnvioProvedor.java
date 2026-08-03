package com.notificacao_api.service.provedor;

import com.notificacao_api.service.queue.ClassificacaoErroEnvio;

public class ExcecaoEnvioProvedor extends RuntimeException {

    private final ClassificacaoErroEnvio classificacao;

    public ExcecaoEnvioProvedor(String message, ClassificacaoErroEnvio classificacao) {
        super(message);
        this.classificacao = classificacao;
    }

    public ExcecaoEnvioProvedor(String message, boolean reenviavel) {
        this(message, reenviavel ? ClassificacaoErroEnvio.REENVIAVEL : ClassificacaoErroEnvio.NAO_REENVIAVEL);
    }

    public ClassificacaoErroEnvio getClassificacao() {
        return classificacao;
    }

    public boolean isReenviavel() {
        return classificacao.reenviavel();
    }
}
