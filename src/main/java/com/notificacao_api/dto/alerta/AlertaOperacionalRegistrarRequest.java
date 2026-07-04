package com.notificacao_api.dto.alerta;

import jakarta.validation.constraints.NotBlank;

public record AlertaOperacionalRegistrarRequest(
        @NotBlank String titulo,
        @NotBlank String mensagem,
        String destinatario,
        String canal,
        String codigoErro,
        Long idNotificacao) {
}
