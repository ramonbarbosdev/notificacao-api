package com.notificacao_api.dto.configuracao;

import java.time.LocalDateTime;

public record ConfiguracaoGlobalResponse(
        Long idConfiguracaoGlobal,
        String nmPlataforma,
        String nmDominioPrincipal,
        String nmEmailSuporte,
        String dsSmtpHost,
        Integer nuSmtpPorta,
        String nmSmtpUsuario,
        Boolean smtpSenhaConfigurada,
        Integer nuTimezonePadrao,
        Boolean flWhatsappProviderPadrao,
        Boolean flApiPublicaHabilitada,
        Boolean flTemplatesHabilitado,
        Boolean flWebhooksHabilitado,
        Boolean flTelegramHabilitado,
        Boolean flEmailHabilitado,
        LocalDateTime dtCriacao,
        LocalDateTime dtAtualizacao) {
}
