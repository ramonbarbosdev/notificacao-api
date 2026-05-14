package com.notificacao_api.dto.whatsapp;

import java.time.LocalDateTime;

public record WhatsappConexaoEvento(
        Long idOrganizacao,
        String tipo,
        String status,
        Boolean podeConectar,
        Long segundosRestantes,
        String mensagem,
        LocalDateTime dataHora) {
}
