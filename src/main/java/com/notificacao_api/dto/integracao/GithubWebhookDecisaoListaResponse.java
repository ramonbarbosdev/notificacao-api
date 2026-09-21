package com.notificacao_api.dto.integracao;

import java.util.List;

public record GithubWebhookDecisaoListaResponse(
        List<GithubWebhookDecisaoResponse> itens,
        int pagina,
        int tamanhoPagina,
        long totalElementos,
        int totalPaginas) {
}
