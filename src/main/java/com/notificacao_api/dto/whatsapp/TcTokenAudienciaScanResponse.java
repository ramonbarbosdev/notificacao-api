package com.notificacao_api.dto.whatsapp;

import java.time.Instant;
import java.util.List;

public record TcTokenAudienciaScanResponse(
        boolean sucesso,
        String erro,
        Long idOrganizacao,
        Instant atualizadoEm,
        boolean gatewayOnline,
        int vidaDiasToken,
        int janelaAlertaDias,
        int filaLookbackDias,
        boolean varreduraGatewayCompleta,
        TcTokenAudienciaKpisResponse kpis,
        List<TcTokenAudienciaLinhaResponse> linhas) {
}
