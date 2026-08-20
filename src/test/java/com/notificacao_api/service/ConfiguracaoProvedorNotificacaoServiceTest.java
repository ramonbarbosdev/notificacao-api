package com.notificacao_api.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.notificacao_api.dto.whatsapp.ProvisionarConfigWhatsappResposta;
import com.notificacao_api.enums.CanalNotificacao;
import com.notificacao_api.model.ConfiguracaoProvedorNotificacao;
import com.notificacao_api.repository.ConfiguracaoProvedorNotificacaoRepository;

@ExtendWith(MockitoExtension.class)
class ConfiguracaoProvedorNotificacaoServiceTest {

    @Mock
    private ConfiguracaoProvedorNotificacaoRepository repository;

    @InjectMocks
    private ConfiguracaoProvedorNotificacaoService service;

    @Test
    void deveCriarConfigWhatsappQuandoNaoExistir() {
        when(repository.findFirstByIdOrganizacaoAndCanal(2L, CanalNotificacao.WHATSAPP))
                .thenReturn(Optional.empty());
        when(repository.save(any(ConfiguracaoProvedorNotificacao.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ProvisionarConfigWhatsappResposta resposta = service.garantirWhatsappAtivo(2L);

        assertTrue(resposta.criada());
        assertFalse(resposta.reativada());
        assertEquals(ConfiguracaoProvedorNotificacaoService.PROVEDOR_WHATSAPP_GATEWAY, resposta.provedor());

        ArgumentCaptor<ConfiguracaoProvedorNotificacao> captor =
                ArgumentCaptor.forClass(ConfiguracaoProvedorNotificacao.class);
        verify(repository).save(captor.capture());
        assertEquals(2L, captor.getValue().getIdOrganizacao());
        assertEquals(CanalNotificacao.WHATSAPP, captor.getValue().getCanal());
        assertTrue(captor.getValue().getAtivo());
    }

    @Test
    void deveReativarConfigWhatsappInativa() {
        ConfiguracaoProvedorNotificacao config = new ConfiguracaoProvedorNotificacao();
        config.setIdOrganizacao(3L);
        config.setCanal(CanalNotificacao.WHATSAPP);
        config.setProvedor(ConfiguracaoProvedorNotificacaoService.PROVEDOR_WHATSAPP_GATEWAY);
        config.setAtivo(false);

        when(repository.findFirstByIdOrganizacaoAndCanal(3L, CanalNotificacao.WHATSAPP))
                .thenReturn(Optional.of(config));
        when(repository.save(config)).thenReturn(config);

        ProvisionarConfigWhatsappResposta resposta = service.garantirWhatsappAtivo(3L);

        assertFalse(resposta.criada());
        assertTrue(resposta.reativada());
        assertTrue(config.getAtivo());
    }

    @Test
    void deveManterConfigWhatsappJaAtiva() {
        ConfiguracaoProvedorNotificacao config = new ConfiguracaoProvedorNotificacao();
        config.setIdOrganizacao(1L);
        config.setCanal(CanalNotificacao.WHATSAPP);
        config.setProvedor(ConfiguracaoProvedorNotificacaoService.PROVEDOR_WHATSAPP_GATEWAY);
        config.setAtivo(true);

        when(repository.findFirstByIdOrganizacaoAndCanal(1L, CanalNotificacao.WHATSAPP))
                .thenReturn(Optional.of(config));

        ProvisionarConfigWhatsappResposta resposta = service.garantirWhatsappAtivo(1L);

        assertFalse(resposta.criada());
        assertFalse(resposta.reativada());
        verify(repository, never()).save(any(ConfiguracaoProvedorNotificacao.class));
    }
}
