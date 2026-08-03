package com.notificacao_api.dto.notificacao;

import java.time.LocalDateTime;

public record FilaResumoResponseDTO(
        long pendente,
        long processando,
        long enviada,
        long falhou,
        long bloqueada,
        String proximoEnvioTexto,
        LocalDateTime proximoEnvioEm,
        LocalDateTime atualizadoEm) {
}
