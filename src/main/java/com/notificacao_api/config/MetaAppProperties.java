package com.notificacao_api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "meta.app")
public record MetaAppProperties(
        String id,
        String embeddedSignupConfigId) {

    public boolean embeddedSignupHabilitado() {
        return id != null && !id.isBlank()
                && embeddedSignupConfigId != null && !embeddedSignupConfigId.isBlank();
    }
}
