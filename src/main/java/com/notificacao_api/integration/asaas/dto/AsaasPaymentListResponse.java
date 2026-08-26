package com.notificacao_api.integration.asaas.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AsaasPaymentListResponse(
        java.util.List<AsaasPaymentResponse> data) {
}
