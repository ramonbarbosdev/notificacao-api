package com.notificacao_api.service.whatsapp;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class WhatsappGatewayErroUtil {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private WhatsappGatewayErroUtil() {
    }

    public static String mensagemTextoGateway(String texto) {
        if (texto == null || texto.isBlank()) {
            return "Falha na comunicacao com o gateway WhatsApp.";
        }
        return normalizarErroEnvioWhatsapp(sanitizarMensagemGateway(texto));
    }

    public static String mensagemDoCorpoResposta(String body) {
        String extraida = extrairMensagemCorpo(body);
        if (extraida == null || extraida.isBlank()) {
            return mensagemParaUsuario(new RuntimeException(body == null ? "" : body));
        }
        return mensagemTextoGateway(extraida);
    }

    public static String normalizarErroEnvioWhatsapp(String texto) {
        if (texto == null || texto.isBlank()) {
            return texto;
        }

        String normalizado = texto.toLowerCase();

        if (ehNumeroNaoEncontrado(normalizado)) {
            return "Numero informado nao encontrado no WhatsApp. "
                    + "Verifique DDI, DDD e numero completo, ou confirme no celular se o contato possui WhatsApp ativo.";
        }

        if (normalizado.contains("463")
                || normalizado.contains("tctoken")
                || normalizado.contains("account restricted")
                || normalizado.contains("conta restrita")
                || normalizado.contains("reachout timelock")
                || normalizado.contains("timelock")) {
            return "WhatsApp bloqueou o envio para este contato (restricao 463). "
                    + "Isso costuma ocorrer com numeros novos, contatos que nunca falaram com voce "
                    + "ou conta com limite temporario de novas conversas. "
                    + "Peça para o destinatario enviar uma mensagem primeiro, use o WhatsApp no celular "
                    + "normalmente por algumas horas e evite disparos em massa.";
        }

        return texto;
    }

    public static String mensagemParaUsuario(Throwable ex) {
        if (ex instanceof RestClientResponseException responseEx) {
            return mensagemHttp(responseEx);
        }
        if (ex instanceof ResourceAccessException accessEx) {
            return mensagemRede(accessEx);
        }
        Throwable cause = ex.getCause();
        if (cause instanceof ConnectException || cause instanceof UnknownHostException) {
            return "O gateway WhatsApp esta indisponivel. Verifique se o servico esta em execucao e tente novamente.";
        }
        if (cause instanceof SocketTimeoutException) {
            return "O gateway WhatsApp demorou para responder. Tente novamente em alguns instantes.";
        }
        if (ex.getMessage() != null && !ex.getMessage().isBlank()) {
            String msg = ex.getMessage().toLowerCase();
            if (msg.contains("connection refused") || msg.contains("connect timed out")) {
                return "Nao foi possivel conectar ao gateway WhatsApp. O servico pode estar desligado.";
            }
        }
        return "Nao foi possivel comunicar com o gateway WhatsApp. Tente novamente ou contate o suporte.";
    }

    private static String mensagemRede(ResourceAccessException ex) {
        Throwable cause = ex.getCause();
        if (cause instanceof SocketTimeoutException) {
            return "O gateway WhatsApp demorou para responder. Tente novamente em alguns instantes.";
        }
        if (cause instanceof ConnectException || cause instanceof UnknownHostException) {
            return "O gateway WhatsApp esta indisponivel. Verifique se o servico esta em execucao e tente novamente.";
        }
        return "Nao foi possivel conectar ao gateway WhatsApp. Verifique a configuracao WHATSAPP_GATEWAY_BASE_URL.";
    }

    private static String mensagemHttp(RestClientResponseException ex) {
        int status = ex.getStatusCode().value();
        String doCorpo = extrairMensagemCorpo(ex.getResponseBodyAsString());

        if (status == 502 || status == 503 || status == 504) {
            return "O gateway WhatsApp esta temporariamente indisponivel. Tente novamente em alguns minutos.";
        }
        if (status == 404) {
            return "Sessao WhatsApp nao encontrada no gateway para esta organizacao.";
        }
        if (doCorpo != null && !doCorpo.isBlank()) {
            return mensagemTextoGateway(doCorpo);
        }
        return "Falha na comunicacao com o gateway WhatsApp (HTTP " + status + ").";
    }

    private static String extrairMensagemCorpo(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            JsonNode node = MAPPER.readTree(body);
            for (String campo : new String[] { "mensagem", "message", "erro", "error" }) {
                JsonNode valor = node.get(campo);
                if (valor != null && valor.isTextual() && !valor.asText().isBlank()) {
                    return valor.asText();
                }
            }
        } catch (Exception ignored) {
            // corpo nao-JSON
        }
        if (body.length() > 200) {
            return body.substring(0, 200);
        }
        return body;
    }

    private static String sanitizarMensagemGateway(String texto) {
        String normalizado = texto.toLowerCase();
        if (normalizado.contains("connection refused") || normalizado.contains("econnrefused")) {
            return "O gateway WhatsApp esta indisponivel. Verifique se o servico esta em execucao.";
        }
        if (normalizado.contains("timeout") || normalizado.contains("timed out")) {
            return "O gateway WhatsApp demorou para responder. Tente novamente.";
        }
        return mensagemTextoGateway(texto);
    }

    private static boolean ehNumeroNaoEncontrado(String normalizado) {
        return contemAlgum(normalizado,
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
                "número inválido");
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
