package com.notificacao_api.dto.integracao;

import com.notificacao_api.enums.CanalNotificacao;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WebhookGenericoRequest(
        CanalNotificacao canal,
        String destinatario,
        String assunto,
        String titulo,
        @NotBlank String mensagem,
        @Size(max = 120) String referenciaExterna) {
}
