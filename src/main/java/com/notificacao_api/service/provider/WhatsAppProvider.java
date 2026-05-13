package com.notificacao_api.service.provider;

import org.springframework.stereotype.Component;

import com.notificacao_api.dto.whatsapp.EnviarMensagemWhatsappRequisicao;
import com.notificacao_api.dto.whatsapp.EnviarMensagemWhatsappResposta;
import com.notificacao_api.enums.CanalNotificacao;
import com.notificacao_api.model.Notification;
import com.notificacao_api.model.NotificationProviderConfig;
import com.notificacao_api.service.whatsapp.WhatsappSessaoService;

@Component
public class WhatsAppProvider implements NotificationProvider {

    private final WhatsappSessaoService whatsappSessaoService;

    public WhatsAppProvider(WhatsappSessaoService whatsappSessaoService) {
        this.whatsappSessaoService = whatsappSessaoService;
    }

    @Override
    public CanalNotificacao getCanal() {
        return CanalNotificacao.WHATSAPP;
    }

    @Override
    public void enviar(Notification notification, NotificationProviderConfig config) {
        EnviarMensagemWhatsappResposta resposta = whatsappSessaoService.enviarMensagemDaOrganizacao(
                notification.getIdOrganizacao(),
                new EnviarMensagemWhatsappRequisicao(notification.getDestinatario(), notification.getMensagem()));

        if (!Boolean.TRUE.equals(resposta.sucesso())) {
            throw new IllegalStateException(resposta.erro() == null
                    ? "Gateway WhatsApp nao confirmou o envio"
                    : resposta.erro());
        }
    }
}
