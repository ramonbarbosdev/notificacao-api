package com.notificacao_api.config;

import java.util.TimeZone;

import io.github.cdimascio.dotenv.Dotenv;

public class DotenvLoader {

    private static final String FUSO_PADRAO = "America/Bahia";

    static {
        Dotenv dotenv = Dotenv.configure()
                .filename(".env")
                .ignoreIfMissing()
                .load();

        dotenv.entries().forEach(entry -> {
            System.setProperty(entry.getKey(), entry.getValue());
            if ("SPRING_PROFILES_ACTIVE".equals(entry.getKey())) {
                System.setProperty("spring.profiles.active", entry.getValue());
            }
        });

        String fuso = primeiroValorNaoVazio(
                dotenv.get("NOTIFICACAO_FUSO_HORARIO"),
                System.getProperty("NOTIFICACAO_FUSO_HORARIO"),
                System.getenv("NOTIFICACAO_FUSO_HORARIO"),
                FUSO_PADRAO);
        TimeZone.setDefault(TimeZone.getTimeZone(fuso));
    }

    private static String primeiroValorNaoVazio(String... valores) {
        for (String valor : valores) {
            if (valor != null && !valor.isBlank()) {
                return valor.trim();
            }
        }
        return FUSO_PADRAO;
    }

    public static void init() {
    }
}
