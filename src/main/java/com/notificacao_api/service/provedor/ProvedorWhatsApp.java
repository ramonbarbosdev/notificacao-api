package com.notificacao_api.service.provedor;

import org.springframework.stereotype.Component;

import com.notificacao_api.dto.whatsapp.EnviarMensagemWhatsappRequisicao;
import com.notificacao_api.dto.whatsapp.EnviarMensagemWhatsappResposta;
import com.notificacao_api.enums.CanalNotificacao;
import com.notificacao_api.model.Notificacao;
import com.notificacao_api.model.ConfiguracaoProvedorNotificacao;
import com.notificacao_api.service.whatsapp.WhatsappSessaoService;

@Component
public class ProvedorWhatsApp implements ProvedorNotificacao {

    private final WhatsappSessaoService whatsappSessaoService;

    public ProvedorWhatsApp(WhatsappSessaoService whatsappSessaoService) {
        this.whatsappSessaoService = whatsappSessaoService;
    }

    @Override
    public CanalNotificacao getCanal() {
        return CanalNotificacao.WHATSAPP;
    }

    @Override
    public void enviar(Notificacao notificacao, ConfiguracaoProvedorNotificacao configuracao) {
        EnviarMensagemWhatsappResposta resposta = whatsappSessaoService.enviarMensagemDaOrganizacao(
                notificacao.getIdOrganizacao(),
                new EnviarMensagemWhatsappRequisicao(notificacao.getDestinatario(), notificacao.getMensagem()));

        if (!Boolean.TRUE.equals(resposta.sucesso())) {
            String erro = resposta.erro() == null
                    ? "Gateway WhatsApp nao confirmou o envio"
                    : resposta.erro();
            throw new ExcecaoEnvioProvedor(erro, erroReenviavel(erro));
        }
    }

    private boolean erroReenviavel(String erro) {
        if (erro == null || erro.isBlank()) {
            return true;
        }

        String normalizado = erro.toLowerCase();
        return !normalizado.contains("numero informado nao encontrado")
                && !normalizado.contains("número informado não encontrado")
                && !normalizado.contains("contato invalido")
                && !normalizado.contains("contato inválido");
    }
}
