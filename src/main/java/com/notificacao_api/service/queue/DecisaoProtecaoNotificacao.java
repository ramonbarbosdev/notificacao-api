package com.notificacao_api.service.queue;

import java.time.LocalDateTime;

public record DecisaoProtecaoNotificacao(
        boolean permitida,
        LocalDateTime tentarNovamenteEm,
        String motivo) {

    public static DecisaoProtecaoNotificacao permitir() {
        return new DecisaoProtecaoNotificacao(true, null, null);
    }

    public static DecisaoProtecaoNotificacao aguardarAte(LocalDateTime tentarNovamenteEm, String motivo) {
        return new DecisaoProtecaoNotificacao(false, tentarNovamenteEm, motivo);
    }
}
