package com.notificacao_api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.notificacao_api.dto.whatsapp.WhatsappConversaResponse;
import com.notificacao_api.dto.whatsapp.WhatsappInboundRequest;
import com.notificacao_api.dto.whatsapp.WhatsappMensagemSessaoLoteRequest;
import com.notificacao_api.service.whatsapp.WhatsappGatewayWebhookValidator;
import com.notificacao_api.service.whatsapp.WhatsappInboundService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/webhooks/whatsapp/gateway")
public class WhatsappGatewayWebhookController {

    private final WhatsappGatewayWebhookValidator webhookValidator;
    private final WhatsappInboundService inboundService;

    public WhatsappGatewayWebhookController(
            WhatsappGatewayWebhookValidator webhookValidator,
            WhatsappInboundService inboundService) {
        this.webhookValidator = webhookValidator;
        this.inboundService = inboundService;
    }

    @PostMapping("/inbound")
    public ResponseEntity<?> receberInbound(
            @RequestHeader(name = "X-API-KEY", required = false) String apiKey,
            @Valid @RequestBody WhatsappInboundRequest request) {

        if (!webhookValidator.chaveValida(apiKey)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "API Key do gateway invalida.");
        }

        return inboundService.processar(request)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.accepted().build());
    }

    @PostMapping("/mensagens/lote")
    public ResponseEntity<Void> receberMensagensLote(
            @RequestHeader(name = "X-API-KEY", required = false) String apiKey,
            @Valid @RequestBody WhatsappMensagemSessaoLoteRequest request) {

        if (!webhookValidator.chaveValida(apiKey)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "API Key do gateway invalida.");
        }

        inboundService.processarLote(request.mensagens());
        return ResponseEntity.accepted().build();
    }
}
