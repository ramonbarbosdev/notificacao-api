package com.notificacao_api.dto.notificacao;

import java.time.LocalDateTime;

import com.notificacao_api.enums.CanalNotificacao;
import com.notificacao_api.enums.StatusNotificacao;

public record FilaNotificacaoResponseDTO(
        Long idNotificacao,
        CanalNotificacao canal,
        String destinatario,
        StatusNotificacao status,
        String provider,
        Integer tentativas,
        LocalDateTime proximaTentativa,
        String erro,
        String motivoAguardando,
        String codigoErro,
        LocalDateTime criadoEm,
        Integer tentativasMaximas,
        LocalDateTime enviadoEm,
        LocalDateTime retomadaPrevistaEm,
        String retomadaPrevistaTexto,
        Long tempoEstimadoEnvioSegundos,
        Integer posicaoFila,
        String tempoEstimadoEnvioTexto,
        LocalDateTime previsaoEnvioEm) {
}
