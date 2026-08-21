package com.notificacao_api.service.whatsapp.provider;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.notificacao_api.enums.WhatsappProvedorEnvio;
import com.notificacao_api.service.whatsapp.WhatsappConfigurationService;

@Component
public class WhatsappProviderFactory {

    private final Map<WhatsappProvedorEnvio, WhatsappEnvioProvider> providers;
    private final WhatsappConfigurationService configurationService;

    public WhatsappProviderFactory(
            List<WhatsappEnvioProvider> providers,
            WhatsappConfigurationService configurationService) {
        this.providers = providers.stream()
                .collect(Collectors.toMap(WhatsappEnvioProvider::getProvedor, Function.identity()));
        this.configurationService = configurationService;
    }

    public WhatsappEnvioProvider resolver(Long idOrganizacao) {
        WhatsappProvedorEnvio provedor = configurationService.provedorAtivo(idOrganizacao);
        WhatsappEnvioProvider impl = providers.get(provedor);
        if (impl == null) {
            throw new IllegalStateException("Provider WhatsApp nao registrado: " + provedor);
        }
        return impl;
    }
}
