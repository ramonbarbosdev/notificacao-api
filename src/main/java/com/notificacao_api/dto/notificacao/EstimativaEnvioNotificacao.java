package com.notificacao_api.dto.notificacao;

public record EstimativaEnvioNotificacao(
        Long tempoEstimadoEnvioSegundos,
        Integer posicaoFila,
        String tempoEstimadoEnvioTexto) {

    public static EstimativaEnvioNotificacao indisponivel() {
        return new EstimativaEnvioNotificacao(null, null, null);
    }
}
