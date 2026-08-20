package com.notificacao_api.service.whatsapp;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.notificacao_api.service.queue.ClassificacaoErroEnvio;

public final class WhatsappGatewayErroUtil {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private WhatsappGatewayErroUtil() {
    }

    public static String mensagemTextoGateway(String texto) {
        if (texto == null || texto.isBlank()) {
            return "Falha na comunicacao com o gateway WhatsApp.";
        }
        return ClassificacaoErroEnvio.mensagemParaUsuario(texto);
    }

    public static String mensagemDoCorpoResposta(String body) {
        String extraida = extrairMensagemCorpo(body);
        if (extraida == null || extraida.isBlank()) {
            return mensagemParaUsuario(new RuntimeException(body == null ? "" : body));
        }
        return mensagemTextoGateway(extraida);
    }

    public static String normalizarErroEnvioWhatsapp(String texto) {
        return ClassificacaoErroEnvio.mensagemParaUsuario(texto);
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
            return ClassificacaoErroEnvio.mensagemParaUsuario(ex.getMessage());
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
}
