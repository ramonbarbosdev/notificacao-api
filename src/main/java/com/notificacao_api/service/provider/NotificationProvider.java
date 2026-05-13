package com.notificacao_api.service.provider;

import com.notificacao_api.enums.CanalNotificacao;
import com.notificacao_api.model.Notification;
import com.notificacao_api.model.NotificationProviderConfig;

public interface NotificationProvider {

    CanalNotificacao getCanal();

    void enviar(Notification notification, NotificationProviderConfig config);
}
