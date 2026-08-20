package com.notificacao_api.dto.whatsapp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GatewaySessaoResumoDTO(
        Long idOrganizacao,
        String pasta,
        Boolean temCredenciais,
        Boolean emMemoria,
        String status,
        Boolean conectado,
        String telefone,
        String erro) {
}
