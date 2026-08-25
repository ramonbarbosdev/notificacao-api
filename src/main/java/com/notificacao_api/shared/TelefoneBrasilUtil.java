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

        // 12 digitos com 9 apos DDI: insere o 9o movel apos o DDD (ex: 557191180200 -> 5571991180200).
        if (digitos.startsWith("55")
                && digitos.length() == 12
                && digitos.charAt(4) == '9'
                && digitos.substring(4).length() == 8) {
            char segundoLocal = digitos.charAt(5);
            if (ehSegundoDigitoLocalMovelValido(segundoLocal)) {
                return corrigirOitavoInseridoIndevidamente(digitos);
            }
            return corrigirOitavoInseridoIndevidamente(digitos.substring(0, 4) + "9" + digitos.substring(4));
        }

        return corrigirOitavoInseridoIndevidamente(digitos);
    }

    /**
     * Desfaz insercao indevida de "8" pela regra antiga que usava "98" em vez de "9".
     * So corrige numeros ja gravados errados (ex: 5571982864312) sem alterar celulares
     * legitimos cujo assinante comeca com 82xx (ex: 5573982229717).
     */
    private static String corrigirOitavoInseridoIndevidamente(String digitos) {
        if (!digitos.startsWith("55") || digitos.length() != 13
                || digitos.charAt(4) != '9' || digitos.charAt(5) != '8') {
            return digitos;
        }

        // Padrao 9-8-9... : oitavo fantasma entre dois 9s (ex: 5571989729330)
        if (digitos.charAt(6) == '9') {
            return digitos.substring(0, 5) + digitos.substring(6);
        }

        // Padrao 9-8-2-8... : corrupcao da regra antiga "98" (ex: 92864312 virou 82864312)
        if (digitos.charAt(6) == '2' && digitos.charAt(7) == '8') {
            String dozeDigitos = digitos.substring(0, 5) + digitos.substring(6);
            String saidaBugada = aplicarRegraAntigaNonoDeslocado(dozeDigitos);
            if (saidaBugada != null && saidaBugada.equals(digitos)) {
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

    /**
     * Normaliza o telefone da sessao WhatsApp: confia em 13 digitos validos do Baileys
     * e so aplica regras de correcao quando o valor ainda nao esta completo.
     */
    public static String normalizarTelefoneSessaoWhatsapp(String telefone) {
        if (telefone == null || telefone.isBlank()) {
            return telefone == null ? "" : telefone;
        }

        String digitos = telefone.replaceAll("\\D", "");
        if (digitos.startsWith("0")) {
            digitos = digitos.substring(1);
        }

        if (celularBrasilComNonoDigito(digitos)) {
            return corrigirOitavoInseridoIndevidamente(digitos);
        }

        return normalizarCelularWhatsapp(telefone);
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
