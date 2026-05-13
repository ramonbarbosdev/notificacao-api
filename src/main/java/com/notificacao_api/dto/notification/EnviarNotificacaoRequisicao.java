package com.notificacao_api.dto.notification;

import com.notificacao_api.enums.CanalNotificacao;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record EnviarNotificacaoRequisicao(
        @NotNull CanalNotificacao canal,
        @NotBlank String destinatario,
        String assunto,
        @NotBlank String mensagem) {
}
