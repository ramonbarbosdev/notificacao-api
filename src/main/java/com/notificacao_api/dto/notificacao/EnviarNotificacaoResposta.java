package com.notificacao_api.dto.notificacao;

import com.notificacao_api.enums.CanalNotificacao;
import com.notificacao_api.enums.StatusNotificacao;

public record EnviarNotificacaoResposta(
        Boolean sucesso,
        Long idNotificacao,
        CanalNotificacao canal,
        StatusNotificacao status,
        String erro,
        String codigoErro,
        String motivoAguardando,
        Integer tentativas,
        Integer tentativasMaximas,
        Long tempoEstimadoEnvioSegundos,
        Integer posicaoFila,
        String tempoEstimadoEnvioTexto) {
}
