package com.notificacao_api.dto.notificacao;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EnviarNotificacaoLoteItemRequisicao(
        @NotBlank String destinatario,
        String assunto,
        @NotBlank String mensagem,
        @Size(max = 120) String referenciaExterna) {
}
