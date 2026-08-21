package com.notificacao_api.service.whatsapp.provider;

import java.util.Map;

import com.notificacao_api.enums.WhatsappProvedorEnvio;
import com.notificacao_api.model.Notificacao;

public interface WhatsappEnvioProvider {

    WhatsappProvedorEnvio getProvedor();

    ResultadoEnvioWhatsapp sendText(Long idOrganizacao, String phone, String message, Notificacao notificacao);

    ResultadoEnvioWhatsapp sendTemplate(
            Long idOrganizacao,
            String phone,
            String templateName,
            String language,
            Map<String, String> parameters,
            Notificacao notificacao);

    ResultadoEnvioWhatsapp sendImage(
            Long idOrganizacao, String phone, String mediaUrl, String caption, Notificacao notificacao);

    ResultadoEnvioWhatsapp sendDocument(
            Long idOrganizacao, String phone, String mediaUrl, String filename, Notificacao notificacao);
}
