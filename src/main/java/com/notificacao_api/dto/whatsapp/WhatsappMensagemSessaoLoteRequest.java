package com.notificacao_api.dto.whatsapp;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record WhatsappMensagemSessaoLoteRequest(
        @NotNull Long idOrganizacao,
        @NotEmpty @Valid List<WhatsappInboundRequest> mensagens) {
}
