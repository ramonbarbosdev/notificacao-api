package com.notificacao_api.integration.asaas.dto;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AsaasSubscriptionResponse(
        String id,
        String customer,
        String billingType,
        String status,
        BigDecimal value,
        String nextDueDate,
        String cycle) {
}
