package com.notificacao_api.dto.notificacao;

import com.notificacao_api.enums.CanalNotificacao;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record EnviarNotificacaoRequisicao(
        @NotNull CanalNotificacao canal,
        @NotBlank String destinatario,
        String assunto,
        @NotBlank String mensagem,
        String chaveModelo,
        String variaveisTemplate) {

    public EnviarNotificacaoRequisicao(
            CanalNotificacao canal,
            String destinatario,
            String assunto,
            String mensagem) {
        this(canal, destinatario, assunto, mensagem, null, null);
    }
}
