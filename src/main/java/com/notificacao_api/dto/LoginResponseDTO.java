package com.notificacao_api.dto;

import java.util.List;

public record LoginResponseDTO(
        String token,
        String tipoGlobal,
        boolean deveSelecionarOrganizacao,
        List<OrganizacaoLoginDTO> organizacoes) {
}
