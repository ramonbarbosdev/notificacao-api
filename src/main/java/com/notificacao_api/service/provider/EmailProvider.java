package com.notificacao_api.service.provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.notificacao_api.enums.CanalNotificacao;
import com.notificacao_api.model.Notification;
import com.notificacao_api.model.NotificationProviderConfig;

@Component
public class EmailProvider implements NotificationProvider {

    private static final Logger log = LoggerFactory.getLogger(EmailProvider.class);

    @Override
    public CanalNotificacao getCanal() {
        return CanalNotificacao.EMAIL;
    }

    @Override
    public void enviar(Notification notification, NotificationProviderConfig config) {
        log.info(
                "Simulando envio de e-mail: organizacao={}, destinatario={}, assunto={}",
                notification.getIdOrganizacao(),
                notification.getDestinatario(),
                notification.getAssunto());
    }
}
