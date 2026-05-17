package com.notificacao_api.dto.template;

import com.notificacao_api.enums.CanalNotificacao;

public record RenderizarTemplateNotificacaoResponseDTO(
        String templateKey,
        CanalNotificacao canal,
        String assunto,
        String mensagem,
        Integer versao) {
}
