package com.notificacao_api.dto.whatsapp;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record WhatsappInboundRequest(
        @NotNull Long idOrganizacao,
        @NotBlank String telefone,
        String jid,
        String idMensagemExterna,
        String tipo,
        String preview,
        String nmContato,
        String recebidaEm) {
}
