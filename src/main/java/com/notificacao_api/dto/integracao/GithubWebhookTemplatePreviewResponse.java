package com.notificacao_api.dto.integracao;

import java.util.List;
import java.util.Map;

public record GithubWebhookTemplatePreviewResponse(
        String assunto,
        String mensagem,
        String textoWhatsapp,
        Map<String, String> variaveisUsadas,
        List<String> variaveisDesconhecidas,
        Map<String, Object> contextoEvento) {
}
