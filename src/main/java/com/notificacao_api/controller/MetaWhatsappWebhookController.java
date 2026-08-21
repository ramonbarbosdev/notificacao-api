package com.notificacao_api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.notificacao_api.config.MetaWebhookProperties;
import com.notificacao_api.service.whatsapp.MetaWebhookService;
import com.notificacao_api.service.whatsapp.MetaWebhookSignatureValidator;

@RestController
@RequestMapping("/webhooks/whatsapp/meta")
public class MetaWhatsappWebhookController {

    private final MetaWebhookProperties webhookProperties;
    private final MetaWebhookSignatureValidator signatureValidator;
    private final MetaWebhookService webhookService;

    public MetaWhatsappWebhookController(
            MetaWebhookProperties webhookProperties,
            MetaWebhookSignatureValidator signatureValidator,
            MetaWebhookService webhookService) {
        this.webhookProperties = webhookProperties;
        this.signatureValidator = signatureValidator;
        this.webhookService = webhookService;
    }

    @GetMapping
    public ResponseEntity<String> verificar(
            @RequestParam(name = "hub.mode", required = false) String mode,
            @RequestParam(name = "hub.verify_token", required = false) String verifyToken,
            @RequestParam(name = "hub.challenge", required = false) String challenge) {

        if (!"subscribe".equals(mode)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Modo de verificacao invalido.");
        }

        String expectedToken = webhookProperties.verifyToken();
        if (expectedToken == null
                || expectedToken.isBlank()
                || !expectedToken.equals(verifyToken)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Verify token invalido.");
        }

        return ResponseEntity.ok(challenge);
    }

    @PostMapping
    public ResponseEntity<Void> receberEventos(
            @RequestHeader(name = "X-Hub-Signature-256", required = false) String signature,
            @RequestBody byte[] rawBody) {

        if (!signatureValidator.assinaturaValida(signature, rawBody)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Assinatura Meta webhook invalida.");
        }

        webhookService.processarPayload(new String(rawBody, java.nio.charset.StandardCharsets.UTF_8));
        return ResponseEntity.ok().build();
    }
}
