package com.notificacao_api.service.queue;

import com.notificacao_api.enums.CodigoErroEnvio;

/**
 * Classifica erros de envio para retry, contagem de falhas da sessao WhatsApp
 * e elegibilidade de bloqueio automatico de contato.
 */
public enum ClassificacaoErroEnvio {

    REENVIAVEL(true, true, false),
    REENVIAVEL_INFRA(true, false, false),
    NAO_REENVIAVEL_DESTINATARIO(false, false, true),
    /** 463 / tctoken / contato novo — falha do destinatario, sem pausar sessao nem bloquear contato. */
    NAO_REENVIAVEL_CONTATO_WHATSAPP(false, false, false),
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

    public boolean restricaoContatoWhatsapp() {
        return this == NAO_REENVIAVEL_CONTATO_WHATSAPP;
    }

    public static ClassificacaoErroEnvio classificar(String erro) {
        return analisar(erro).classificacao();
    }

    public static String mensagemParaUsuario(String textoBruto) {
        return analisar(textoBruto).mensagemUsuario();
    }

    public static CodigoErroEnvio codigoDe(String textoBruto) {
        return analisar(textoBruto).codigo();
    }

    public static Resultado analisar(String textoBruto) {
        if (textoBruto == null || textoBruto.isBlank()) {
            return new Resultado(
                    "Falha na comunicacao com o gateway WhatsApp.",
                    CodigoErroEnvio.GENERICO,
                    REENVIAVEL);
        }

        String normalizado = textoBruto.toLowerCase();

        if (contemAlgum(normalizado,
                "is not defined",
                "referenceerror",
                "cannot read propert")) {
            return new Resultado(
                    "Erro interno no gateway WhatsApp ao processar o envio. "
                            + "Reinicie o gateway e tente novamente.",
                    CodigoErroEnvio.GATEWAY_INDISPONIVEL,
                    REENVIAVEL_INFRA);
        }

        if (contemAlgum(normalizado,
                "connection refused",
                "econnrefused",
                "connect timed out",
                "connection reset",
                "econnreset",
                "unknownhost",
                "unknown host")) {
            return new Resultado(
                    "O gateway WhatsApp esta indisponivel. Verifique se o servico esta em execucao.",
                    CodigoErroEnvio.GATEWAY_INDISPONIVEL,
                    REENVIAVEL_INFRA);
        }

        if (contemAlgum(normalizado, "timeout", "timed out")) {
            if (contemAlgum(normalizado,
                    "nao confirmou a entrega",
                    "nao devolveu recibo",
                    "timed out waiting for message",
                    "tokens de privacidade",
                    "nao sincronizou tokens de privacidade",
                    "usync fetch yielded no results")) {
                return new Resultado(
                        "WhatsApp nao confirmou a entrega para este contato. "
                                + "O servidor nao devolveu recibo ou nao sincronizou tokens de privacidade (restricao 463). "
                                + "Peca para o destinatario enviar a primeira mensagem e tente novamente.",
                        CodigoErroEnvio.WHATSAPP_RESTRICAO_463,
                        NAO_REENVIAVEL_CONTATO_WHATSAPP);
            }

            return new Resultado(
                    "O gateway WhatsApp demorou para responder. Tente novamente.",
                    CodigoErroEnvio.GATEWAY_INDISPONIVEL,
                    REENVIAVEL_INFRA);
        }

        if (contemAlgum(normalizado,
                "numero informado nao encontrado",
                "número informado não encontrado",
                "numero nao encontrado no whatsapp",
                "número não encontrado no whatsapp",
                "not registered on whatsapp",
                "nao esta no whatsapp",
                "não está no whatsapp",
                "is not on whatsapp",
                "invalid number",
                "numero invalido",
                "número inválido",
                "contato invalido",
                "contato inválido",
                "destinatario invalido",
                "destinatário inválido")) {
            return new Resultado(
                    "Numero informado nao encontrado no WhatsApp. "
                            + "Verifique DDI, DDD e numero completo, ou confirme no celular se o contato possui WhatsApp ativo.",
                    CodigoErroEnvio.WHATSAPP_NUMERO_INVALIDO,
                    NAO_REENVIAVEL_DESTINATARIO);
        }

        if (contemAlgum(normalizado,
                "nao possui tctoken",
                "nao pronto para envio",
                "token de conversa",
                "reach-out timelock",
                "sessao linkada nao possui tctoken")) {
            return new Resultado(
                    "Esta sessao WhatsApp ainda nao esta pronta para enviar para este contato (sem tctoken). "
                            + "Peca para o contato enviar uma mensagem de texto para o numero conectado, "
                            + "ou responda uma vez pelo celular nesta sessao. "
                            + "Tentativas sem token aumentam o risco de restricao 463 (reach-out timelock).",
                    CodigoErroEnvio.WHATSAPP_RESTRICAO_463,
                    NAO_REENVIAVEL_CONTATO_WHATSAPP);
        }

        if (contemAlgum(normalizado,
                "nao conseguiu preparar o envio",
                "não conseguiu preparar o envio",
                "lid indisponivel",
                "lid indisponível",
                "usync fetch yielded no results")) {
            return new Resultado(
                    "WhatsApp nao conseguiu preparar o envio para este contato. "
                            + "Contatos novos precisam enviar a primeira mensagem para este WhatsApp antes de receber mensagens. "
                            + "Peca para o destinatario mandar um \"oi\" e tente novamente.",
                    CodigoErroEnvio.WHATSAPP_RESTRICAO_463,
                    NAO_REENVIAVEL_CONTATO_WHATSAPP);
        }

        if (contemAlgum(normalizado,
                "463",
                "tctoken",
                "account restricted",
                "conta restrita",
                "restricao 463",
                "restrição 463",
                "reachout timelock",
                "timelock",
                "limite temporario de novas conversas",
                "limite temporário de novas conversas")) {
            return new Resultado(
                    "WhatsApp bloqueou o envio (restricao 463 / reach-out timelock). "
                            + "Isso costuma ocorrer com contatos sem tctoken, numeros novos ou limite temporario "
                            + "de novas conversas na conta. Peça para o destinatario enviar uma mensagem de texto "
                            + "primeiro e evite tentativas repetidas em massa nesta sessao.",
                    CodigoErroEnvio.WHATSAPP_RESTRICAO_463,
                    NAO_REENVIAVEL_CONTATO_WHATSAPP);
        }

        if (contemAlgum(normalizado,
                "whatsapp nao conectado",
                "whatsapp não conectado")) {
            return new Resultado(
                    textoBruto,
                    CodigoErroEnvio.WHATSAPP_NAO_CONECTADO,
                    NAO_REENVIAVEL_INFRA);
        }

        if (contemAlgum(normalizado,
                "sessao nao iniciada",
                "sessão não iniciada",
                "sessao do whatsapp nao iniciada",
                "sessão do whatsapp não iniciada")) {
            return new Resultado(
                    textoBruto,
                    CodigoErroEnvio.WHATSAPP_SESSAO_NAO_INICIADA,
                    NAO_REENVIAVEL_INFRA);
        }

        if (contemAlgum(normalizado,
                "configuracao ativa nao encontrada",
                "configuração ativa não encontrada",
                "provedor nao implementado",
                "provedor não implementado",
                "processamento interrompido")) {
            return new Resultado(
                    textoBruto,
                    CodigoErroEnvio.CONFIGURACAO_INDISPONIVEL,
                    NAO_REENVIAVEL_INFRA);
        }

        if (contemAlgum(normalizado,
                "502",
                "503",
                "504",
                "429",
                "rate limit",
                "too many requests",
                "gateway",
                "servico indisponivel",
                "serviço indisponível",
                "temporarily unavailable",
                "socket")) {
            return new Resultado(
                    textoBruto,
                    CodigoErroEnvio.GATEWAY_INDISPONIVEL,
                    REENVIAVEL_INFRA);
        }

        return new Resultado(textoBruto, CodigoErroEnvio.GENERICO, REENVIAVEL);
    }

    private static boolean contemAlgum(String texto, String... termos) {
        for (String termo : termos) {
            if (texto.contains(termo)) {
                return true;
            }
        }
        return false;
    }

    public record Resultado(
            String mensagemUsuario,
            CodigoErroEnvio codigo,
            ClassificacaoErroEnvio classificacao) {
    }
}
