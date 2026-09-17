package com.notificacao_api.service.github;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

public final class GithubWhatsappOptInSupport {

    public static final String FRASE_ATIVACAO_PADRAO =
            "Quero receber notificação, do github!";

    private static final Pattern GITHUB_LOGIN =
            Pattern.compile("^[a-zA-Z0-9](?:[a-zA-Z0-9]|-(?=[a-zA-Z0-9])){0,38}$");

    private GithubWhatsappOptInSupport() {}

    public static String resolverFraseAtivacao(String fraseConfigurada) {
        if (fraseConfigurada == null || fraseConfigurada.isBlank()) {
            return FRASE_ATIVACAO_PADRAO;
        }
        return fraseConfigurada.trim();
    }

    public static boolean correspondeFraseAtivacao(String textoMensagem, String fraseConfigurada) {
        if (textoMensagem == null || textoMensagem.isBlank()) {
            return false;
        }
        String esperado = normalizarComparacao(resolverFraseAtivacao(fraseConfigurada));
        String recebido = normalizarComparacao(textoMensagem);
        return recebido.equals(esperado) || recebido.startsWith(esperado);
    }

    public static boolean loginGithubValido(String candidato) {
        if (candidato == null) {
            return false;
        }
        String login = candidato.trim();
        if (login.isEmpty() || login.length() > 39) {
            return false;
        }
        if (login.startsWith("@")) {
            login = login.substring(1);
        }
        return GITHUB_LOGIN.matcher(login).matches();
    }

    public static String extrairLoginGithub(String texto) {
        if (texto == null) {
            return null;
        }
        String login = texto.trim();
        if (login.startsWith("@")) {
            login = login.substring(1).trim();
        }
        return loginGithubValido(login) ? login.toLowerCase(Locale.ROOT) : null;
    }

    public static String montarLinkWaMe(String telefoneOrigemDigits, String frase) {
        if (telefoneOrigemDigits == null || telefoneOrigemDigits.isBlank()) {
            return null;
        }
        String digits = telefoneOrigemDigits.replaceAll("\\D", "");
        if (digits.isEmpty()) {
            return null;
        }
        String texto = resolverFraseAtivacao(frase);
        String encoded = URLEncoder.encode(texto, StandardCharsets.UTF_8);
        return "https://wa.me/" + digits + "?text=" + encoded;
    }

    static String normalizarComparacao(String texto) {
        String semAcentos = Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return semAcentos
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }
}
