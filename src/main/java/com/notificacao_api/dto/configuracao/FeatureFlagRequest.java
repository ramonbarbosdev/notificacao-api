package com.notificacao_api.dto.configuracao;

import java.util.Map;

import com.notificacao_api.enums.RecursoFeature;

public record FeatureFlagRequest(
        Map<RecursoFeature, Boolean> features) {
}
