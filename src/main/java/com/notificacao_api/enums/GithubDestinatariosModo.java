package com.notificacao_api.enums;

import java.util.Locale;

public enum GithubDestinatariosModo {
    RESPONSAVEIS,
    RESPONSAVEIS_E_MOVIMENTADOR,
    LOGINS_CONFIGURADOS;

    public static GithubDestinatariosModo fromString(String valor) {
        if (valor == null || valor.isBlank()) {
            return RESPONSAVEIS;
        }
        try {
            return GithubDestinatariosModo.valueOf(valor.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return RESPONSAVEIS;
        }
    }
}
