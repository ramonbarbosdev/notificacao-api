package com.notificacao_api.service.provedor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.notificacao_api.enums.CanalNotificacao;
import com.notificacao_api.model.Notificacao;
import com.notificacao_api.model.ConfiguracaoProvedorNotificacao;

@Component
public class ProvedorTelegram implements ProvedorNotificacao {

    private static final Logger log = LoggerFactory.getLogger(ProvedorTelegram.class);

    @Override
    public CanalNotificacao getCanal() {
        return CanalNotificacao.TELEGRAM;
    }

    @Override
    public void enviar(Notificacao notificacao, ConfiguracaoProvedorNotificacao configuracao) {
        log.info(
                "Simulando envio Telegram: organizacao={}, destinatario={}",
                notificacao.getIdOrganizacao(),
                notificacao.getDestinatario());
    }
}
