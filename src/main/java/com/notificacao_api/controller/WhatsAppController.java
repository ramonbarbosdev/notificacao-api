package com.notificacao_api.controller;

import com.notificacao_api.dto.whatsapp.EnviarMensagemWhatsappRequisicao;
import com.notificacao_api.dto.notificacao.EnviarNotificacaoRequisicao;
import com.notificacao_api.dto.notificacao.EnviarNotificacaoResposta;
import com.notificacao_api.dto.whatsapp.StatusWhatsappResposta;
import com.notificacao_api.enums.CanalNotificacao;
import com.notificacao_api.service.NotificacaoService;
import com.notificacao_api.service.whatsapp.WhatsappSessaoService;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/app/whatsapp")
public class WhatsAppController {

    private final WhatsappSessaoService whatsappSessaoService;
    private final NotificacaoService notificacaoService;

    public WhatsAppController(
            WhatsappSessaoService whatsappSessaoService,
            NotificacaoService notificacaoService) {
        this.whatsappSessaoService = whatsappSessaoService;
        this.notificacaoService = notificacaoService;
    }

    @PostMapping("/conectar")
    public StatusWhatsappResposta conectar() {
        return whatsappSessaoService.conectar();
    }

    @GetMapping("/status")
    public StatusWhatsappResposta obterStatus() {
        return whatsappSessaoService.obterStatus();
    }

    @PostMapping("/enviar-mensagem")
    public EnviarNotificacaoResposta enviarMensagem(
            @Valid @RequestBody EnviarMensagemWhatsappRequisicao requisicao) {
        return notificacaoService.enviar(new EnviarNotificacaoRequisicao(
                CanalNotificacao.WHATSAPP,
                requisicao.telefone(),
                null,
                requisicao.mensagem()));
    }

    @PostMapping("/desconectar")
    public StatusWhatsappResposta desconectar() {
        return whatsappSessaoService.desconectar();
    }

    @PostMapping("/cancelar-conexao")
    public StatusWhatsappResposta cancelarConexao() {
        return whatsappSessaoService.desconectar();
    }
}
