package com.notificacao_api.dto.notificacao;

public record CancelarNotificacaoLoteResponse(
        int cancelados,
        int ignorados,
        int totalSolicitados) {
}
