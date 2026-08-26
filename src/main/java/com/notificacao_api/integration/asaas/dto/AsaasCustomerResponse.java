package com.notificacao_api.integration.asaas.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AsaasCustomerResponse(
        String id,
        String name,
        String cpfCnpj,
        String email) {
}
