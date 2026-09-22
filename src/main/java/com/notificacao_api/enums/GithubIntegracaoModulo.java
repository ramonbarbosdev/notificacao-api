package com.notificacao_api.enums;

public enum GithubIntegracaoModulo {
    PROJECTS_V2,
    ISSUE_COMMENT;

    public String codigo() {
        return name();
    }

    public static GithubIntegracaoModulo fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Modulo GitHub invalido");
        }
        return GithubIntegracaoModulo.valueOf(raw.trim().toUpperCase());
    }
}
