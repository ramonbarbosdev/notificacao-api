package com.notificacao_api.dto.pagamento;

import java.math.BigDecimal;

public record PlanoDisponivelResponse(
        Long idPlano,
        String nmPlano,
        String dsPlano,
        BigDecimal vlMensal,
        Integer nuDiasTrial,
        Integer nuLimiteMensagensMensal,
        Integer nuLimiteUsuarios,
        Integer nuLimiteTemplates,
        Boolean flWhatsappHabilitado,
        Boolean flEmailHabilitado,
        Boolean flTelegramHabilitado,
        Boolean flWebhookHabilitado,
        Boolean flApiPublicaHabilitada) {
}
