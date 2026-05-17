package com.notificacao_api.dto.template;

import com.notificacao_api.enums.CanalNotificacao;

public record TemplateNotificacaoFilter(
        String termo,
        CanalNotificacao canal,
        Boolean ativo) {
}
