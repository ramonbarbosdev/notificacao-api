package com.notificacao_api.service.whatsapp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.JsonNode;
import com.notificacao_api.config.MetaAppProperties;
import com.notificacao_api.config.MetaWebhookProperties;
import com.notificacao_api.config.WhatsappMetaProperties;

@Service
public class MetaEmbeddedSignupService {

    private static final Logger log = LoggerFactory.getLogger(MetaEmbeddedSignupService.class);

    private final RestClient restClient;
    private final MetaAppProperties metaAppProperties;
    private final MetaWebhookProperties metaWebhookProperties;
    private final WhatsappMetaProperties whatsappMetaProperties;

    public MetaEmbeddedSignupService(
            RestClient.Builder restClientBuilder,
            MetaAppProperties metaAppProperties,
            MetaWebhookProperties metaWebhookProperties,
            WhatsappMetaProperties whatsappMetaProperties) {
        this.restClient = restClientBuilder
                .baseUrl(whatsappMetaProperties.graphBaseUrl())
                .build();
        this.metaAppProperties = metaAppProperties;
        this.metaWebhookProperties = metaWebhookProperties;
        this.whatsappMetaProperties = whatsappMetaProperties;
    }

    public String trocarCodigoPorToken(String code) {
        validarConfiguracaoEmbeddedSignup();

        if (code == null || code.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Codigo OAuth do Embedded Signup e obrigatorio.");
        }

        String appSecret = metaWebhookProperties.appSecret();
        if (appSecret == null || appSecret.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "META_APP_SECRET nao configurado no servidor.");
        }

        try {
            JsonNode resposta = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v21.0/oauth/access_token")
                            .queryParam("client_id", metaAppProperties.id().trim())
                            .queryParam("client_secret", appSecret.trim())
                            .queryParam("code", code.trim())
                            .build())
                    .retrieve()
                    .body(JsonNode.class);

            if (resposta == null || !resposta.hasNonNull("access_token")) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "Meta nao retornou access token para o Embedded Signup.");
            }

            return resposta.get("access_token").asText();
        } catch (HttpStatusCodeException ex) {
            log.warn("Falha ao trocar codigo Embedded Signup: status={} body={}",
                    ex.getStatusCode().value(),
                    ex.getResponseBodyAsString());
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Nao foi possivel concluir o Embedded Signup na Meta. Verifique app, config_id e permissoes.");
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("Erro inesperado no Embedded Signup: {}", ex.getMessage());
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Nao foi possivel concluir o Embedded Signup na Meta.");
        }
    }

    public void validarConfiguracaoEmbeddedSignup() {
        if (!metaAppProperties.embeddedSignupHabilitado()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Embedded Signup nao configurado. Defina META_APP_ID e META_EMBEDDED_SIGNUP_CONFIG_ID.");
        }
    }
}
