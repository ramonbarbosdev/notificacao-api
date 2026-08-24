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
        String recebidaEm,
        String direcao) {

    public WhatsappInboundRequest(
            Long idOrganizacao,
            String telefone,
            String jid,
            String idMensagemExterna,
            String tipo,
            String preview,
            String nmContato,
            String recebidaEm) {
        this(idOrganizacao, telefone, jid, idMensagemExterna, tipo, preview, nmContato, recebidaEm, null);
    }
}
