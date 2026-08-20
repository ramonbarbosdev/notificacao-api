package com.notificacao_api.dto.contato;

public record SincronizarContatosWhatsappResponseDTO(
        int importados,
        int atualizados,
        int removidos,
        int totalGateway) {
}
