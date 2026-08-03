package com.notificacao_api.service.queue;

/**
 * Classifica erros de envio para retry, contagem de falhas da sessao WhatsApp
 * e elegibilidade de bloqueio automatico de contato.
 */
public enum ClassificacaoErroEnvio {

    REENVIAVEL(true, true, false),
    NAO_REENVIAVEL_DESTINATARIO(false, false, true),
    NAO_REENVIAVEL_INFRA(false, false, false),
    NAO_REENVIAVEL(false, false, false);

    private final boolean reenviavel;
    private final boolean contaFalhaSessao;
    private final boolean bloqueioContatoImediato;

    ClassificacaoErroEnvio(boolean reenviavel, boolean contaFalhaSessao, boolean bloqueioContatoImediato) {
        this.reenviavel = reenviavel;
        this.contaFalhaSessao = contaFalhaSessao;
        this.bloqueioContatoImediato = bloqueioContatoImediato;
    }

    public boolean reenviavel() {
        return reenviavel;
    }

    public boolean contaFalhaSessao() {
        return contaFalhaSessao;
    }

    public boolean bloqueioContatoImediato() {
        return bloqueioContatoImediato;
    }

    public static ClassificacaoErroEnvio classificar(String erro) {
        if (erro == null || erro.isBlank()) {
            return REENVIAVEL;
        }

        String texto = erro.toLowerCase();

        if (contemAlgum(texto,
                "numero informado nao encontrado",
                "número informado não encontrado",
                "contato invalido",
                "contato inválido",
                "numero invalido",
                "número inválido",
                "destinatario invalido",
                "destinatário inválido",
                "not registered on whatsapp",
                "não está no whatsapp",
                "nao esta no whatsapp")) {
            return NAO_REENVIAVEL_DESTINATARIO;
        }

        if (contemAlgum(texto,
                "whatsapp nao conectado",
                "whatsapp não conectado",
                "sessao do whatsapp nao iniciada",
                "sessão do whatsapp não iniciada",
                "configuracao ativa nao encontrada",
                "configuração ativa não encontrada",
                "provedor nao implementado",
                "provedor não implementado",
                "processamento interrompido")) {
            return NAO_REENVIAVEL_INFRA;
        }

        if (contemAlgum(texto,
                "timeout",
                "timed out",
                "connection refused",
                "connection reset",
                "503",
                "502",
                "504",
                "429",
                "rate limit",
                "too many requests",
                "gateway",
                "servico indisponivel",
                "serviço indisponível",
                "temporarily unavailable",
                "econnreset",
                "socket")) {
            return REENVIAVEL;
        }

        return REENVIAVEL;
    }

    private static boolean contemAlgum(String texto, String... termos) {
        for (String termo : termos) {
            if (texto.contains(termo)) {
                return true;
            }
        }
        return false;
    }
}
