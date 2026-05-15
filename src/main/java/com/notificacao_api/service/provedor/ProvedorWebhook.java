package com.notificacao_api.service.provedor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.notificacao_api.enums.CanalNotificacao;
import com.notificacao_api.model.Notificacao;
import com.notificacao_api.model.ConfiguracaoProvedorNotificacao;

@Component
public class ProvedorWebhook implements ProvedorNotificacao {

    private static final Logger log = LoggerFactory.getLogger(ProvedorWebhook.class);

    @Override
    public CanalNotificacao getCanal() {
        return CanalNotificacao.WEBHOOK;
    }

    @Override
    public void enviar(Notificacao notificacao, ConfiguracaoProvedorNotificacao configuracao) {
        log.info(
                "Simulando envio Webhook: organizacao={}, destinatario={}",
                notificacao.getIdOrganizacao(),
                notificacao.getDestinatario());
    }
}
