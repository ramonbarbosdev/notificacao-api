package com.notificacao_api.dto.whatsapp;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TcTokenAudienciaPedirConfirmacaoRequest(
        @NotBlank String telefone,
        @Size(max = 4096) String mensagem,
        Boolean modoTeste) {

    public TcTokenAudienciaPedirConfirmacaoRequest(String telefone, String mensagem) {
        this(telefone, mensagem, null);
    }
}
