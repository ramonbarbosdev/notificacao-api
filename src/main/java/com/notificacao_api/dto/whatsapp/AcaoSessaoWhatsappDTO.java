package com.notificacao_api.dto.whatsapp;

public record AcaoSessaoWhatsappDTO(
        String codigo,
        String rotulo,
        String descricao,
        boolean primaria,
        boolean habilitada) {
}
