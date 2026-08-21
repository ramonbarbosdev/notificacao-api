package com.notificacao_api.service.whatsapp.provider;

import java.util.Map;

import org.springframework.stereotype.Component;

import com.notificacao_api.dto.whatsapp.EnviarMensagemWhatsappRequisicao;
import com.notificacao_api.dto.whatsapp.EnviarMensagemWhatsappResposta;
import com.notificacao_api.enums.WhatsappProvedorEnvio;
import com.notificacao_api.model.Notificacao;
import com.notificacao_api.service.provedor.ProvedorWhatsApp;
import com.notificacao_api.service.whatsapp.WhatsappSessaoService;

@Component
public class BaileysWhatsappProvider implements WhatsappEnvioProvider {

    private final WhatsappSessaoService whatsappSessaoService;

    public BaileysWhatsappProvider(WhatsappSessaoService whatsappSessaoService) {
        this.whatsappSessaoService = whatsappSessaoService;
    }

    @Override
    public WhatsappProvedorEnvio getProvedor() {
        return WhatsappProvedorEnvio.WHATSAPP_GATEWAY;
    }

    @Override
    public ResultadoEnvioWhatsapp sendText(Long idOrganizacao, String phone, String message, Notificacao notificacao) {
        EnviarMensagemWhatsappResposta resposta = whatsappSessaoService.enviarMensagemDaOrganizacao(
                idOrganizacao,
                new EnviarMensagemWhatsappRequisicao(phone, message));

        if (!Boolean.TRUE.equals(resposta.sucesso())
                || resposta.idMensagem() == null
                || resposta.idMensagem().isBlank()) {
            String erro = resposta.erro() == null
                    ? "Gateway WhatsApp nao confirmou o envio da mensagem"
                    : resposta.erro();
            return ResultadoEnvioWhatsapp.falha(erro);
        }

        if (Boolean.FALSE.equals(resposta.confirmado())) {
            return new ResultadoEnvioWhatsapp(
                    resposta.idMensagem(),
                    false,
                    com.notificacao_api.enums.WhatsappMensagemStatus.SENT,
                    ProvedorWhatsApp.AVISO_ACK_NAO_PROPAGOU);
        }

        return ResultadoEnvioWhatsapp.confirmado(resposta.idMensagem());
    }

    @Override
    public ResultadoEnvioWhatsapp sendTemplate(
            Long idOrganizacao,
            String phone,
            String templateName,
            String language,
            Map<String, String> parameters,
            Notificacao notificacao) {
        return sendText(idOrganizacao, phone, notificacao.getMensagem(), notificacao);
    }

    @Override
    public ResultadoEnvioWhatsapp sendImage(
            Long idOrganizacao, String phone, String mediaUrl, String caption, Notificacao notificacao) {
        return ResultadoEnvioWhatsapp.falha("Envio de imagem nao suportado pelo provider Baileys nesta versao.");
    }

    @Override
    public ResultadoEnvioWhatsapp sendDocument(
            Long idOrganizacao, String phone, String mediaUrl, String filename, Notificacao notificacao) {
        return ResultadoEnvioWhatsapp.falha("Envio de documento nao suportado pelo provider Baileys nesta versao.");
    }
}
