package com.notificacao_api.shared;

import com.notificacao_api.enums.CanalNotificacao;

public final class TelefoneBrasilUtil {

    private TelefoneBrasilUtil() {
    }

    /**
     * Normaliza destino para persistencia e comparacao.
     * Para WhatsApp, converte para E.164 sem + (ex: 5571981180200).
     */
    public static String normalizarDestino(CanalNotificacao canal, String destinatario) {
        if (destinatario == null) {
            return "";
        }

        String apenasDigitos = destinatario.replaceAll("\\D", "");
        if (canal == CanalNotificacao.WHATSAPP) {
            return normalizarCelularWhatsapp(apenasDigitos);
        }

        return apenasDigitos;
    }

    public static String normalizarCelularWhatsapp(String telefone) {
        if (telefone == null || telefone.isBlank()) {
            return telefone == null ? "" : telefone;
        }

        String digitos = telefone.replaceAll("\\D", "");
        if (digitos.startsWith("0")) {
            digitos = digitos.substring(1);
        }

        // 11 digitos: DDD + celular com 9 (ex: 71981180200)
        if (digitos.length() == 11 && digitos.charAt(2) == '9') {
            return "55" + digitos;
        }

        // 10 digitos: DDD + celular antigo sem 9 (ex: 7181180200)
        if (digitos.length() == 10 && ehDigitoCelularAntigo(digitos.charAt(2))) {
            return "55" + digitos.substring(0, 2) + "9" + digitos.substring(2);
        }

        // 10 digitos com 9 apos DDD (digitacao incompleta)
        if (digitos.length() == 10 && digitos.charAt(2) == '9') {
            return "55" + digitos;
        }

        // 13 digitos com DDI
        if (digitos.startsWith("55") && digitos.length() == 13) {
            return digitos;
        }

        // 12 digitos com DDI: insere 9 apenas quando o local comeca com 7 ou 8
        if (digitos.startsWith("55")
                && digitos.length() == 12
                && ehDigitoCelularAntigo(digitos.charAt(4))) {
            return digitos.substring(0, 4) + "9" + digitos.substring(4);
        }

        // 12 digitos com 9 deslocado (ex: 557191180200 -> 5571981180200)
        if (digitos.startsWith("55")
                && digitos.length() == 12
                && digitos.charAt(4) == '9'
                && digitos.substring(4).length() == 8) {
            return digitos.substring(0, 4) + "98" + digitos.substring(5);
        }

        return digitos;
    }

    public static boolean celularBrasilComNonoDigito(String telefone) {
        if (telefone == null) {
            return false;
        }
        String digitos = telefone.replaceAll("\\D", "");
        return digitos.startsWith("55") && digitos.length() == 13 && digitos.charAt(4) == '9';
    }

    private static boolean ehDigitoCelularAntigo(char digito) {
        return digito == '7' || digito == '8';
    }
}
