package com.notificacao_api.dto.configuracao;

public record ConfiguracaoGlobalRequest(
        String nmPlataforma,
        String nmDominioPrincipal,
        String nmEmailSuporte,
        String dsSmtpHost,
        Integer nuSmtpPorta,
        String nmSmtpUsuario,
        String dsSmtpSenha,
        Integer nuTimezonePadrao,
        Boolean flWhatsappProviderPadrao,
        Boolean flApiPublicaHabilitada,
        Boolean flTemplatesHabilitado,
        Boolean flWebhooksHabilitado,
        Boolean flTelegramHabilitado,
        Boolean flEmailHabilitado) {
}
