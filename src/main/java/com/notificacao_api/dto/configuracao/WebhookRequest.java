package com.notificacao_api.dto.configuracao;

import java.util.Set;

import com.notificacao_api.enums.WebhookEvento;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

public record WebhookRequest(
        @NotBlank String nome,
        @NotBlank String url,
        String secret,
        @NotEmpty Set<WebhookEvento> eventos,
        Boolean ativo) {
}
