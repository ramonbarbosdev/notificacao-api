package com.notificacao_api.integration.asaas.dto;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AsaasSubscriptionRequest(
        String customer,
        String billingType,
        BigDecimal value,
        String nextDueDate,
        String cycle,
        String creditCardToken,
        String description) {
}
