package com.notificacao_api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.notificacao_api.integration.asaas.AsaasClient;
import com.notificacao_api.integration.asaas.dto.AsaasWebhookPayload;
import com.notificacao_api.service.AsaasWebhookService;

@RestController
@RequestMapping("/webhooks/asaas")
public class AsaasWebhookController {

    private final AsaasClient asaasClient;
    private final AsaasWebhookService webhookService;

    public AsaasWebhookController(AsaasClient asaasClient, AsaasWebhookService webhookService) {
        this.asaasClient = asaasClient;
        this.webhookService = webhookService;
    }

    @PostMapping
    public ResponseEntity<Void> receber(
            @RequestHeader(name = "asaas-access-token", required = false) String accessToken,
            @RequestBody AsaasWebhookPayload payload) {

        if (!asaasClient.webhookTokenValido(accessToken)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Token do webhook Asaas invalido.");
        }

        webhookService.processar(payload);
        return ResponseEntity.accepted().build();
    }
}
