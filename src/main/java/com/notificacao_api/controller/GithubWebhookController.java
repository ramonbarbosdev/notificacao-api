package com.notificacao_api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.notificacao_api.enums.ApiKeyScope;
import com.notificacao_api.model.OrganizacaoApiKey;
import com.notificacao_api.service.OrganizacaoApiKeyAuthenticationService;
import com.notificacao_api.service.github.GithubWebhookService;
import com.notificacao_api.service.github.GithubWebhookSignatureValidator;

@RestController
@RequestMapping("/webhooks/github")
public class GithubWebhookController {

    private final OrganizacaoApiKeyAuthenticationService apiKeyAuthenticationService;
    private final GithubWebhookSignatureValidator signatureValidator;
    private final GithubWebhookService webhookService;

    public GithubWebhookController(
            OrganizacaoApiKeyAuthenticationService apiKeyAuthenticationService,
            GithubWebhookSignatureValidator signatureValidator,
            GithubWebhookService webhookService) {
        this.apiKeyAuthenticationService = apiKeyAuthenticationService;
        this.signatureValidator = signatureValidator;
        this.webhookService = webhookService;
    }

    @PostMapping
    public ResponseEntity<Void> receberEvento(
            @RequestHeader(name = "X-API-KEY", required = false) String headerApiKey,
            @RequestParam(name = "key", required = false) String queryApiKey,
            @RequestHeader(name = "X-Hub-Signature-256", required = false) String signature,
            @RequestHeader(name = "X-GitHub-Event", required = false) String githubEvent,
            @RequestHeader(name = "X-GitHub-Delivery", required = false) String deliveryId,
            @RequestBody byte[] rawBody) {

        String apiKey = StringUtils.hasText(headerApiKey) ? headerApiKey.trim() : queryApiKey;
        if (!StringUtils.hasText(apiKey)) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Informe a API Key no header X-API-KEY ou na query key= (necessario para o GitHub App).");
        }

        OrganizacaoApiKey chave = apiKeyAuthenticationService.autenticar(apiKey);
        apiKeyAuthenticationService.validarScope(chave, ApiKeyScope.NOTIFICACOES_ENVIAR);

        if (!signatureValidator.assinaturaValida(signature, rawBody, apiKey)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Assinatura GitHub webhook invalida.");
        }

        String payload = new String(rawBody, java.nio.charset.StandardCharsets.UTF_8);
        webhookService.processar(chave.getIdOrganizacao(), githubEvent, deliveryId, payload);
        return ResponseEntity.ok().build();
    }
}
