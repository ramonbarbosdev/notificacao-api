package com.notificacao_api.dto.whatsapp;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GatewaySessoesListaResponseDTO(
        Boolean sucesso,
        List<GatewaySessaoResumoDTO> sessoes,
        String erro) {
}
