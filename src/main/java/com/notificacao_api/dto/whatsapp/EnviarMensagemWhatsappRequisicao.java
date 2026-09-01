package com.notificacao_api.dto.whatsapp;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

public record EnviarMensagemWhatsappRequisicao(
        @NotBlank String telefone,
        @NotBlank String mensagem,
        String tipo,
        String tituloLista,
        String textoBotaoLista,
        @Valid List<OpcaoWhatsapp> opcoes) {

    public EnviarMensagemWhatsappRequisicao(String telefone, String mensagem) {
        this(telefone, mensagem, "TEXT", null, null, null);
    }

    public String tipoNormalizado() {
        return tipo != null && !tipo.isBlank() ? tipo.trim().toUpperCase() : "TEXT";
    }
}
