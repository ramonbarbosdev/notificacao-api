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
            return corrigirOitavoInseridoIndevidamente("55" + digitos);
        }

        // 10 digitos: DDD + celular antigo sem 9 (ex: 7181180200)
        if (digitos.length() == 10 && ehDigitoCelularAntigo(digitos.charAt(2))) {
            return corrigirOitavoInseridoIndevidamente("55" + digitos.substring(0, 2) + "9" + digitos.substring(2));
        }

        // 10 digitos com 9 apos DDD (digitacao incompleta)
        if (digitos.length() == 10 && digitos.charAt(2) == '9') {
            return corrigirOitavoInseridoIndevidamente("55" + digitos);
        }

        // 13 digitos com DDI
        if (digitos.startsWith("55") && digitos.length() == 13) {
            if (celularBrasilComNonoDigito(digitos)) {
                return corrigirOitavoInseridoIndevidamente(digitos);
            }

            String reprocessado = normalizarCelularWhatsapp(digitos.substring(2));
            if (celularBrasilComNonoDigito(reprocessado)) {
                return corrigirOitavoInseridoIndevidamente(reprocessado);
            }
        }

        // 12 digitos com DDI: insere 9 apenas quando o local comeca com 7 ou 8
        if (digitos.startsWith("55")
                && digitos.length() == 12
                && ehDigitoCelularAntigo(digitos.charAt(4))) {
            return corrigirOitavoInseridoIndevidamente(digitos.substring(0, 4) + "9" + digitos.substring(4));
        }

        // 12 digitos com 9 deslocado (ex: 557191180200 -> 5571981180200).
        // Nao aplicar quando o numero local ja e movel valido (ex: 557199729330).
        if (digitos.startsWith("55")
                && digitos.length() == 12
                && digitos.charAt(4) == '9'
                && digitos.substring(4).length() == 8
                && !ehSegundoDigitoLocalMovelValido(digitos.charAt(5))) {
            if (digitos.charAt(5) == '2') {
                return corrigirOitavoInseridoIndevidamente(digitos.substring(0, 4) + "9" + digitos.substring(4));
            }
            return corrigirOitavoInseridoIndevidamente(digitos.substring(0, 4) + "98" + digitos.substring(5));
        }

        return corrigirOitavoInseridoIndevidamente(digitos);
    }

    /**
     * Remove o "8" inserido por engano pela regra antiga de nono digito deslocado
     * (ex: 5571982864312 -> 5571992864312, 5571989729330 -> 557199729330).
     */
    private static String corrigirOitavoInseridoIndevidamente(String digitos) {
        if (!digitos.startsWith("55") || digitos.length() != 13 || digitos.charAt(4) != '9' || digitos.charAt(5) != '8') {
            return digitos;
        }

        if (digitos.charAt(6) == '9') {
            return digitos.substring(0, 5) + digitos.substring(6);
        }

        String candidatoDozeDigitos = digitos.substring(0, 5) + digitos.substring(6);
        if (candidatoDozeDigitos.length() == 12
                && candidatoDozeDigitos.charAt(4) == '9'
                && candidatoDozeDigitos.charAt(5) == '2'
                && digitos.charAt(6) != '8') {
            String reproduzido = aplicarRegraAntigaNonoDeslocado(candidatoDozeDigitos);
            if (reproduzido != null && reproduzido.equals(digitos)) {
                return digitos.substring(0, 5) + "9" + digitos.substring(6);
            }
        }

        return digitos;
    }

    private static String aplicarRegraAntigaNonoDeslocado(String digitos) {
        if (digitos.startsWith("55")
                && digitos.length() == 12
                && digitos.charAt(4) == '9'
                && digitos.substring(4).length() == 8
                && !ehSegundoDigitoLocalMovelValido(digitos.charAt(5))) {
            return digitos.substring(0, 4) + "98" + digitos.substring(5);
        }

        return null;
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

    private static boolean ehSegundoDigitoLocalMovelValido(char digito) {
        return digito == '7' || digito == '8' || digito == '9';
    }

    public static boolean nomePareceTelefone(String nome, String telefone) {
        if (nome == null || nome.isBlank()) {
            return true;
        }

        String digitosNome = nome.replaceAll("\\D", "");
        if (digitosNome.length() < 8 || !digitosNome.matches("\\d+")) {
            return false;
        }

        if (telefone == null || telefone.isBlank()) {
            return digitosNome.length() >= 10;
        }

        String digitosTelefone = telefone.replaceAll("\\D", "");
        return digitosNome.equals(digitosTelefone)
                || digitosTelefone.endsWith(digitosNome)
                || digitosNome.endsWith(digitosTelefone);
    }

    public static String resolverNomeContatoWhatsapp(String nomeInformado, String telefone) {
        if (nomeInformado != null
                && !nomeInformado.isBlank()
                && !nomePareceTelefone(nomeInformado.trim(), telefone)) {
            return nomeInformado.trim();
        }

        return null;
    }
}
