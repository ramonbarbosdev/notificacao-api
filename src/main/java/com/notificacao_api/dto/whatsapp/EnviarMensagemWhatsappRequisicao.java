package com.notificacao_api.dto.whatsapp;

import jakarta.validation.constraints.NotBlank;

public record EnviarMensagemWhatsappRequisicao(
        @NotBlank String telefone,
        @NotBlank String mensagem
) {}
