package com.notificacao_api.dto.whatsapp;

import java.util.List;

public record SessaoOperacionalContextoDTO(
        String statusOperacional,
        int falhasConsecutivas,
        int maximoFalhasConsecutivas,
        String dtPausadoAte,
        String titulo,
        String explicacao,
        String orientacao,
        List<AcaoSessaoWhatsappDTO> acoes) {
}
