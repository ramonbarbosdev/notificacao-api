package com.notificacao_api.service.provedor;

public record ResultadoEnvioProvedor(
        Boolean confirmadoEntrega,
        String avisoEnvio) {

    public static ResultadoEnvioProvedor confirmado() {
        return new ResultadoEnvioProvedor(true, null);
    }

    public static ResultadoEnvioProvedor enviadoSemConfirmacaoEntrega(String avisoEnvio) {
        return new ResultadoEnvioProvedor(false, avisoEnvio);
    }
}
