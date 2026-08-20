package com.notificacao_api.dto.notificacao;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;

public record CancelarNotificacaoLoteRequest(
        List<Long> ids,
        Long idOrganizacao,
        Boolean somenteCancelaveis,
        String motivo) {

    public boolean usaIds() {
        return ids != null && !ids.isEmpty();
    }

    public boolean usaFiltroOrganizacao() {
        return idOrganizacao != null && Boolean.TRUE.equals(somenteCancelaveis);
    }
}
