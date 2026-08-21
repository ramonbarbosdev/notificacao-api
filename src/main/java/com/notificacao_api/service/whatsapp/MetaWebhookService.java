package com.notificacao_api.service.whatsapp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.notificacao_api.enums.WhatsappMensagemStatus;
import com.notificacao_api.model.WhatsappConfiguracao;
import com.notificacao_api.model.WhatsappWebhookEvento;
import com.notificacao_api.repository.WhatsappWebhookEventoRepository;

@Service
public class MetaWebhookService {

    private static final Logger log = LoggerFactory.getLogger(MetaWebhookService.class);

    private final ObjectMapper objectMapper;
    private final WhatsappConfigurationService configurationService;
    private final WhatsappMensagemService mensagemService;
    private final WhatsappWebhookEventoRepository webhookEventoRepository;

    public MetaWebhookService(
            ObjectMapper objectMapper,
            WhatsappConfigurationService configurationService,
            WhatsappMensagemService mensagemService,
            WhatsappWebhookEventoRepository webhookEventoRepository) {
        this.objectMapper = objectMapper;
        this.configurationService = configurationService;
        this.mensagemService = mensagemService;
        this.webhookEventoRepository = webhookEventoRepository;
    }

    @Transactional
    public void processarPayload(String payload) {
        JsonNode root;
        try {
            root = objectMapper.readTree(payload);
        } catch (Exception ex) {
            log.warn("Meta webhook payload invalido: {}", ex.getMessage());
            return;
        }

        if (!"whatsapp_business_account".equals(root.path("object").asText())) {
            return;
        }

        JsonNode entries = root.get("entry");
        if (entries == null || !entries.isArray()) {
            return;
        }

        for (JsonNode entry : entries) {
            JsonNode changes = entry.get("changes");
            if (changes == null || !changes.isArray()) {
                continue;
            }
            for (JsonNode change : changes) {
                processarChange(change);
            }
        }
    }

    private void processarChange(JsonNode change) {
        JsonNode value = change.get("value");
        if (value == null) {
            return;
        }

        String phoneNumberId = value.path("metadata").path("phone_number_id").asText(null);
        if (phoneNumberId == null || phoneNumberId.isBlank()) {
            return;
        }

        WhatsappConfiguracao config = configurationService.buscarPorPhoneNumberId(phoneNumberId).orElse(null);
        if (config == null) {
            log.warn("Meta webhook sem configuracao para phoneNumberId={}", phoneNumberId);
            return;
        }

        JsonNode statuses = value.get("statuses");
        if (statuses != null && statuses.isArray()) {
            for (JsonNode statusNode : statuses) {
                processarStatus(config.getIdOrganizacao(), statusNode);
            }
        }
    }

    private void processarStatus(Long idOrganizacao, JsonNode statusNode) {
        String messageId = statusNode.path("id").asText(null);
        String status = statusNode.path("status").asText(null);
        if (messageId == null || status == null) {
            return;
        }

        String idEvento = messageId + ":" + status + ":" + statusNode.path("timestamp").asText("");
        if (webhookEventoRepository.existsByIdEventoMeta(idEvento)) {
            return;
        }

        WhatsappMensagemStatus novoStatus = mapearStatus(status);
        String codigoErro = null;
        String erro = null;
        if (novoStatus == WhatsappMensagemStatus.FAILED) {
            JsonNode errors = statusNode.get("errors");
            if (errors != null && errors.isArray() && !errors.isEmpty()) {
                codigoErro = errors.get(0).path("code").asText(null);
                erro = errors.get(0).path("title").asText(null);
            }
        }

        mensagemService.atualizarStatusPorIdExterno(messageId, novoStatus, codigoErro, erro);

        WhatsappWebhookEvento evento = new WhatsappWebhookEvento();
        evento.setIdEventoMeta(idEvento);
        webhookEventoRepository.save(evento);

        log.info(
                "Meta webhook status tenantId={} externalMessageId={} status={}",
                idOrganizacao,
                messageId,
                novoStatus);
    }

    private WhatsappMensagemStatus mapearStatus(String status) {
        return switch (status.toLowerCase()) {
            case "sent" -> WhatsappMensagemStatus.SENT;
            case "delivered" -> WhatsappMensagemStatus.DELIVERED;
            case "read" -> WhatsappMensagemStatus.READ;
            case "failed" -> WhatsappMensagemStatus.FAILED;
            default -> WhatsappMensagemStatus.PENDING;
        };
    }
}
