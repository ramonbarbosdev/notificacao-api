package com.notificacao_api.dto.notificacao;

import com.notificacao_api.enums.CanalNotificacao;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record EnviarNotificacaoRequisicao(
        @NotNull CanalNotificacao canal,
        @NotBlank String destinatario,
        String assunto,
        @NotBlank String mensagem,
        String chaveModelo,
        String variaveisTemplate,
        @Size(max = 120) String referenciaExterna) {

    public EnviarNotificacaoRequisicao(
            CanalNotificacao canal,
            String destinatario,
            String assunto,
            String mensagem) {
        this(canal, destinatario, assunto, mensagem, null, null, null);
    }

    public EnviarNotificacaoRequisicao(
            CanalNotificacao canal,
            String destinatario,
            String assunto,
            String mensagem,
            String chaveModelo,
            String variaveisTemplate) {
        this(canal, destinatario, assunto, mensagem, chaveModelo, variaveisTemplate, null);
    }
}
