package com.notificacao_api.dto.configuracao;

import jakarta.validation.constraints.Size;

public record TcTokenConfirmacaoAutomaticaMensagemPadraoRequest(
        @Size(max = 4096) String mensagemPadrao) {}
