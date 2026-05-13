package com.notificacao_api.config;

import io.github.cdimascio.dotenv.Dotenv;

public class DotenvLoader {
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
    }

    public static void init() {
    }
}
