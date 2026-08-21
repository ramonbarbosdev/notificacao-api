package com.notificacao_api.service.whatsapp.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.notificacao_api.enums.WhatsappProvedorEnvio;
import com.notificacao_api.service.whatsapp.WhatsappConfigurationService;

@ExtendWith(MockitoExtension.class)
class WhatsappProviderFactoryTest {

    @Mock
    private WhatsappConfigurationService configurationService;

    @Mock
    private BaileysWhatsappProvider baileysProvider;

    @Mock
    private MetaCloudWhatsappProvider metaProvider;

    private WhatsappProviderFactory factory;

    @BeforeEach
    void setUp() {
        when(baileysProvider.getProvedor()).thenReturn(WhatsappProvedorEnvio.WHATSAPP_GATEWAY);
        when(metaProvider.getProvedor()).thenReturn(WhatsappProvedorEnvio.META_CLOUD);
        factory = new WhatsappProviderFactory(List.of(baileysProvider, metaProvider), configurationService);
    }

    @Test
    void resolveBaileys() {
        when(configurationService.provedorAtivo(1L)).thenReturn(WhatsappProvedorEnvio.WHATSAPP_GATEWAY);
        assertSame(baileysProvider, factory.resolver(1L));
    }

    @Test
    void resolveMeta() {
        when(configurationService.provedorAtivo(2L)).thenReturn(WhatsappProvedorEnvio.META_CLOUD);
        assertSame(metaProvider, factory.resolver(2L));
    }

    @Test
    void provedorEnumValues() {
        assertEquals(2, WhatsappProvedorEnvio.values().length);
    }
}
