package com.notificacao_api.shared;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TextoTemplateUtil {

    private static final Pattern VARIAVEL_PATTERN =
            Pattern.compile("\\{\\{\\s*([\\p{L}_][\\p{L}0-9_.-]*)\\s*}}");

    private TextoTemplateUtil() {
    }

    public static String substituir(String texto, Map<String, String> variaveis) {
        if (texto == null) {
            return null;
        }

        Matcher matcher = VARIAVEL_PATTERN.matcher(texto);
        StringBuffer resultado = new StringBuffer();

        while (matcher.find()) {
            String nome = matcher.group(1);
            String valor = variaveis == null ? "" : variaveis.getOrDefault(nome, "");
            matcher.appendReplacement(resultado, Matcher.quoteReplacement(valor));
        }

        matcher.appendTail(resultado);
        return resultado.toString();
    }
}
