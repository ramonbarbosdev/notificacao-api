package com.notificacao_api.dto.notificacao;

import java.util.List;

public record FilaNotificacaoResponseDTO(
        List<FilaNotificacaoItemDTO> itens) {
}
