package com.notificacao_api.integration.asaas.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AsaasWebhookPayload(
        String id,
        String event,
        AsaasPaymentResponse payment,
        AsaasSubscriptionResponse subscription) {
}
