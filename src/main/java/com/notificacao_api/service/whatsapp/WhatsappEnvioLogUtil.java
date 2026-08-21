package com.notificacao_api.service.whatsapp;

public final class WhatsappEnvioLogUtil {

    private WhatsappEnvioLogUtil() {
    }

    public static String mascararTelefone(String telefone) {
        if (telefone == null || telefone.length() < 6) {
            return "***";
        }
        int visivelInicio = Math.min(4, telefone.length() - 3);
        int visivelFim = 3;
        String inicio = telefone.substring(0, visivelInicio);
        String fim = telefone.substring(telefone.length() - visivelFim);
        int mascarados = telefone.length() - visivelInicio - visivelFim;
        return inicio + "*".repeat(Math.max(mascarados, 3)) + fim;
    }
}
