package com.notificacao_api.dto.whatsapp;

import java.time.LocalDateTime;

public record WhatsappConexaoEvento(
        Long idOrganizacao,
        String tipo,
        String status,
        Boolean podeConectar,
        Long segundosRestantes,
        String mensagem,
        LocalDateTime dataHora,
        WhatsappConversaResponse conversa) {

    public WhatsappConexaoEvento(
            Long idOrganizacao,
            String tipo,
            String status,
            Boolean podeConectar,
            Long segundosRestantes,
            String mensagem,
            LocalDateTime dataHora) {
        this(idOrganizacao, tipo, status, podeConectar, segundosRestantes, mensagem, dataHora, null);
    }
}
