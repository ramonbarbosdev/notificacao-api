package com.notificacao_api.service.provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.notificacao_api.enums.CanalNotificacao;
import com.notificacao_api.model.Notification;
import com.notificacao_api.model.NotificationProviderConfig;

@Component
public class WebhookProvider implements NotificationProvider {

    private static final Logger log = LoggerFactory.getLogger(WebhookProvider.class);

    @Override
    public CanalNotificacao getCanal() {
        return CanalNotificacao.WEBHOOK;
    }

    @Override
    public void enviar(Notification notification, NotificationProviderConfig config) {
        log.info(
                "Simulando envio Webhook: organizacao={}, destinatario={}",
                notification.getIdOrganizacao(),
                notification.getDestinatario());
    }
}
