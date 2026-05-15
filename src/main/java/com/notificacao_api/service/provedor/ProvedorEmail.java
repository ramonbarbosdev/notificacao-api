package com.notificacao_api.service.provedor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.notificacao_api.enums.CanalNotificacao;
import com.notificacao_api.model.Notificacao;
import com.notificacao_api.model.ConfiguracaoProvedorNotificacao;

@Component
public class ProvedorEmail implements ProvedorNotificacao {

    private static final Logger log = LoggerFactory.getLogger(ProvedorEmail.class);

    @Override
    public CanalNotificacao getCanal() {
        return CanalNotificacao.EMAIL;
    }

    @Override
    public void enviar(Notificacao notificacao, ConfiguracaoProvedorNotificacao configuracao) {
        log.info(
                "Simulando envio de e-mail: organizacao={}, destinatario={}, assunto={}",
                notificacao.getIdOrganizacao(),
                notificacao.getDestinatario(),
                notificacao.getAssunto());
    }
}
