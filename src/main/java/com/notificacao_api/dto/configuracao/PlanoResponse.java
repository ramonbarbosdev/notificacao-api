package com.notificacao_api.dto.configuracao;

import java.time.LocalDateTime;

public record PlanoResponse(
        Long idPlano,
        String nmPlano,
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
        Boolean flAtivo,
        LocalDateTime dtCriacao,
        LocalDateTime dtAtualizacao) {
}
