package com.notificacao_api.dto.configuracao;

import com.notificacao_api.enums.RecursoFeature;

public record FeatureFlagResponse(
        Long idFeatureFlag,
        Long idOrganizacao,
        RecursoFeature recurso,
        Boolean habilitado) {
}
