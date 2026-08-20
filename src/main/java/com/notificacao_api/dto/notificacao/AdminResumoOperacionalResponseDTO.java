package com.notificacao_api.dto.notificacao;

import java.util.List;

public record AdminResumoOperacionalResponseDTO(
        List<AdminOrganizacaoOperacionalResumoDTO> organizacoes) {
}
