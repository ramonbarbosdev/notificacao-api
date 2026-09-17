package com.notificacao_api.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.notificacao_api.dto.notificacao.EnviarNotificacaoResposta;
import com.notificacao_api.enums.ApiKeyScope;
import com.notificacao_api.model.OrganizacaoApiKey;
import com.notificacao_api.service.OrganizacaoApiKeyAuthenticationService;
import com.notificacao_api.service.github.GithubWebhookSignatureValidator;
import com.notificacao_api.service.webhook.GenericWebhookService;

import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/webhooks/generico")
public class GenericWebhookController {

    private final OrganizacaoApiKeyAuthenticationService apiKeyAuthenticationService;
    private final GithubWebhookSignatureValidator signatureValidator;
    private final GenericWebhookService webhookService;

    public GenericWebhookController(
            OrganizacaoApiKeyAuthenticationService apiKeyAuthenticationService,
            GithubWebhookSignatureValidator signatureValidator,
            GenericWebhookService webhookService) {
        this.apiKeyAuthenticationService = apiKeyAuthenticationService;
        this.signatureValidator = signatureValidator;
        this.webhookService = webhookService;
    }

    @PostMapping
    public ResponseEntity<EnviarNotificacaoResposta> receberEvento(
            @RequestHeader(name = "X-API-KEY", required = false) String headerApiKey,
            @RequestParam(name = "key", required = false) String queryApiKey,
            @RequestHeader(name = "X-Webhook-Signature-256", required = false) String signature,
            @RequestHeader(name = "X-Webhook-Delivery", required = false) String deliveryId,
            @RequestHeader(name = "Content-Type", required = false) String contentType,
            @RequestBody byte[] rawBody) {

        String apiKey = StringUtils.hasText(headerApiKey) ? headerApiKey.trim() : queryApiKey;
        if (!StringUtils.hasText(apiKey)) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Informe a API Key no header X-API-KEY ou na query key=.");
        }

        OrganizacaoApiKey chave = apiKeyAuthenticationService.autenticar(apiKey);
        apiKeyAuthenticationService.validarScope(chave, ApiKeyScope.NOTIFICACOES_ENVIAR);

        if (StringUtils.hasText(signature)
                && !signatureValidator.assinaturaValida(signature, rawBody, apiKey)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Assinatura do webhook invalida.");
        }

        String payload = new String(rawBody, java.nio.charset.StandardCharsets.UTF_8);
        EnviarNotificacaoResposta resposta = webhookService.processar(
                chave.getIdOrganizacao(),
                contentType,
                deliveryId,
                payload);
        return ResponseEntity.ok(resposta);
    }
}
