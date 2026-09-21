package com.notificacao_api.dto.integracao;

import java.time.LocalDateTime;
import java.util.Map;

public record GithubWebhookDecisaoResponse(
        Long id,
        String deliveryId,
        String githubEvent,
        String action,
        String resultado,
        String descricao,
        String tituloCard,
        String statusDestino,
        String statusAnterior,
        boolean pullRequest,
        boolean issueProjectV2,
        String fluxoDestinatarios,
        String loginsDestino,
        int whatsappEnfileirados,
        Map<String, Object> detalhe,
        LocalDateTime dtCriacao) {
}
