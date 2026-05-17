package com.notificacao_api.dto.configuracao;

import java.time.LocalDateTime;
import java.util.Set;

import com.notificacao_api.enums.WebhookEvento;

public record WebhookResponse(
        Long idWebhook,
        String nome,
        String url,
        Boolean secretConfigurado,
        Set<WebhookEvento> eventos,
        Boolean ativo,
        LocalDateTime dtCriacao,
        LocalDateTime dtAtualizacao) {
}
