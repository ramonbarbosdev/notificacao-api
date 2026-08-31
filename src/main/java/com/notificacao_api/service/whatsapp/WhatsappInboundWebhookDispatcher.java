package com.notificacao_api.service.whatsapp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.notificacao_api.dto.whatsapp.WhatsappInboundRequest;
import com.notificacao_api.dto.whatsapp.WhatsappInboundWebhookPayload;
import com.notificacao_api.model.OrganizacaoConfiguracao;
import com.notificacao_api.repository.OrganizacaoConfiguracaoRepository;
import com.notificacao_api.service.OrganizacaoWebhookInboundService;

@Service
public class WhatsappInboundWebhookDispatcher {

    private static final Logger log = LoggerFactory.getLogger(WhatsappInboundWebhookDispatcher.class);

    private final OrganizacaoConfiguracaoRepository configuracaoRepository;
    private final OrganizacaoWebhookInboundService webhookInboundService;
    private final RestClient restClient;

    public WhatsappInboundWebhookDispatcher(
            OrganizacaoConfiguracaoRepository configuracaoRepository,
            OrganizacaoWebhookInboundService webhookInboundService,
            RestClient.Builder restClientBuilder) {
        this.configuracaoRepository = configuracaoRepository;
        this.webhookInboundService = webhookInboundService;
        this.restClient = restClientBuilder.build();
    }

    @Async
    public void encaminhar(WhatsappInboundRequest request) {
        if (request == null || request.idOrganizacao() == null) {
            return;
        }

        OrganizacaoConfiguracao config = configuracaoRepository
                .findByIdOrganizacao(request.idOrganizacao())
                .orElse(null);

        if (config == null || !webhookInboundService.deveEncaminhar(config)) {
            return;
        }

        String secret = webhookInboundService.resolverSecret(config);
        if (secret == null || secret.isBlank()) {
            log.warn(
                    "Webhook inbound ignorado: secret indisponivel org={}",
                    request.idOrganizacao());
            return;
        }

        WhatsappInboundWebhookPayload payload = new WhatsappInboundWebhookPayload(
                request.idOrganizacao(),
                request.telefone(),
                request.preview(),
                request.idMensagemExterna(),
                request.tipo(),
                request.nmContato(),
                request.recebidaEm());

        try {
            restClient.post()
                    .uri(config.getWebhookInboundUrl())
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .header("X-Webhook-Secret", secret)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception ex) {
            log.warn(
                    "Falha ao encaminhar webhook inbound org={} url={} erro={}",
                    request.idOrganizacao(),
                    config.getWebhookInboundUrl(),
                    ex.getMessage());
        }
    }
}
