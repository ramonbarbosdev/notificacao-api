package com.notificacao_api.service.provedor;

import com.notificacao_api.enums.CanalNotificacao;
import com.notificacao_api.model.Notificacao;
import com.notificacao_api.model.ConfiguracaoProvedorNotificacao;

public interface ProvedorNotificacao {

    CanalNotificacao getCanal();

    void enviar(Notificacao notificacao, ConfiguracaoProvedorNotificacao configuracao);
}
