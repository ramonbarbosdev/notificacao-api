package com.notificacao_api.integration.asaas.dto;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AsaasPaymentResponse(
        String id,
        String customer,
        String subscription,
        String status,
        String billingType,
        BigDecimal value,
        String dueDate,
        String paymentDate,
        String pixCopiaECola,
        String encodedImage,
        String invoiceUrl) {
}
