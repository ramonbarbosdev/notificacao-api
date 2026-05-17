package com.notificacao_api.dto.configuracao;

import jakarta.validation.constraints.NotBlank;

public record PlanoRequest(
        @NotBlank String nmPlano,
        String dsPlano,
        Integer nuLimiteMensagensMensal,
        Integer nuLimiteUsuarios,
        Integer nuLimiteTemplates,
        Integer nuLimiteContatos,
        Boolean flWhatsappHabilitado,
        Boolean flEmailHabilitado,
        Boolean flTelegramHabilitado,
        Boolean flWebhookHabilitado,
        Boolean flApiPublicaHabilitada,
        Boolean flAtivo) {
}
