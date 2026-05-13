package com.notificacao_api.controller;

import com.notificacao_api.dto.whatsapp.EnviarMensagemWhatsappRequisicao;
import com.notificacao_api.dto.whatsapp.EnviarMensagemWhatsappResposta;
import com.notificacao_api.dto.whatsapp.StatusWhatsappResposta;
import com.notificacao_api.service.whatsapp.WhatsappSessaoService;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/app/whatsapp")
public class WhatsAppController {

    private final WhatsappSessaoService whatsappSessaoService;

    public WhatsAppController(WhatsappSessaoService whatsappSessaoService) {
        this.whatsappSessaoService = whatsappSessaoService;
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
    public EnviarMensagemWhatsappResposta enviarMensagem(
            @Valid @RequestBody EnviarMensagemWhatsappRequisicao requisicao) {
        return whatsappSessaoService.enviarMensagem(requisicao);
    }

    @PostMapping("/desconectar")
    public StatusWhatsappResposta desconectar() {
        return whatsappSessaoService.desconectar();
    }
}
