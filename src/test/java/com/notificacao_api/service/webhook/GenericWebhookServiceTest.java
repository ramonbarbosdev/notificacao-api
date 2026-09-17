package com.notificacao_api.service.webhook;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.notificacao_api.dto.notificacao.EnviarNotificacaoRequisicao;
import com.notificacao_api.dto.notificacao.EnviarNotificacaoResposta;
import com.notificacao_api.enums.CanalNotificacao;
import com.notificacao_api.enums.StatusNotificacao;
import java.util.Optional;

import com.notificacao_api.model.OrganizacaoConfiguracao;
import com.notificacao_api.repository.OrganizacaoConfiguracaoRepository;
import com.notificacao_api.service.FeatureFlagService;
import com.notificacao_api.service.NotificacaoService;
import com.notificacao_api.service.OrganizacaoConfiguracaoService;

@ExtendWith(MockitoExtension.class)
class GenericWebhookServiceTest {

    @Mock
    private FeatureFlagService featureFlagService;
    @Mock
    private OrganizacaoConfiguracaoRepository configuracaoRepository;
    @Mock
    private OrganizacaoConfiguracaoService organizacaoConfiguracaoService;
    @Mock
    private NotificacaoService notificacaoService;

    private GenericWebhookService service;

    @BeforeEach
    void setUp() {
        service = new GenericWebhookService(
                featureFlagService,
                configuracaoRepository,
                organizacaoConfiguracaoService,
                new ObjectMapper(),
                notificacaoService);
    }

    private OrganizacaoConfiguracao configurarOrg(long idOrganizacao) {
        OrganizacaoConfiguracao config = new OrganizacaoConfiguracao();
        config.setIdOrganizacao(idOrganizacao);
        when(configuracaoRepository.findByIdOrganizacao(idOrganizacao)).thenReturn(Optional.of(config));
        return config;
    }

    @Test
    void jsonComDestinatarioEnfileiraEnvio() {
        configurarOrg(1L);
        String json = """
                {"destinatario":"5571999999999","mensagem":"ola","referenciaExterna":"evt-1"}
                """;

        when(notificacaoService.enviarParaOrganizacao(eq(1L), any(EnviarNotificacaoRequisicao.class)))
                .thenReturn(new EnviarNotificacaoResposta(
                        true, 10L, CanalNotificacao.WHATSAPP, StatusNotificacao.PENDENTE,
                        null, null, null, 0, 3, null, null, null));

        EnviarNotificacaoResposta resposta =
                service.processar(1L, "application/json", "d1", json);

        assertEquals(10L, resposta.idNotificacao());
        verify(notificacaoService).enviarParaOrganizacao(eq(1L), any(EnviarNotificacaoRequisicao.class));
        verify(notificacaoService, never()).enfileirarSomenteRegistroFila(any(), any(), any(), any());
    }

    @Test
    void jsonSemDestinatarioRegistraNaFila() {
        OrganizacaoConfiguracao config = configurarOrg(1L);
        when(organizacaoConfiguracaoService.deveRegistrarFilaSemDestinatario(config)).thenReturn(true);
        String json = """
                {"mensagem":"alerta sem numero"}
                """;

        when(notificacaoService.enfileirarSomenteRegistroFila(
                        eq(1L), any(EnviarNotificacaoRequisicao.class), eq("webhook:sem-destinatario"), any()))
                .thenReturn(new EnviarNotificacaoResposta(
                        false, 11L, CanalNotificacao.WHATSAPP, StatusNotificacao.BLOQUEADA,
                        "bloqueado", null, null, 0, 3, null, null, null));

        EnviarNotificacaoResposta resposta =
                service.processar(1L, "application/json", null, json);

        assertEquals(11L, resposta.idNotificacao());
        verify(notificacaoService).enfileirarSomenteRegistroFila(
                eq(1L), any(EnviarNotificacaoRequisicao.class), eq("webhook:sem-destinatario"), any());
    }

    @Test
    void semDestinatarioComRegistroDesabilitadoIgnora() {
        OrganizacaoConfiguracao config = configurarOrg(1L);
        when(organizacaoConfiguracaoService.deveRegistrarFilaSemDestinatario(config)).thenReturn(false);

        EnviarNotificacaoResposta resposta = service.processar(
                1L, "application/json", null, "{\"mensagem\":\"x\"}");

        assertEquals(false, resposta.sucesso());
        verify(notificacaoService, never()).enfileirarSomenteRegistroFila(any(), any(), any(), any());
    }

    @Test
    void textoPuroUsaCorpoComoMensagem() {
        OrganizacaoConfiguracao config = configurarOrg(2L);
        when(organizacaoConfiguracaoService.deveRegistrarFilaSemDestinatario(config)).thenReturn(true);

        when(notificacaoService.enfileirarSomenteRegistroFila(
                        eq(2L), any(EnviarNotificacaoRequisicao.class), eq("webhook:sem-destinatario"), any()))
                .thenReturn(new EnviarNotificacaoResposta(
                        false, 12L, CanalNotificacao.WHATSAPP, StatusNotificacao.BLOQUEADA,
                        null, null, null, 0, 3, null, null, null));

        service.processar(2L, "text/plain", "entrega-1", "Mensagem simples");

        verify(notificacaoService).enfileirarSomenteRegistroFila(
                eq(2L), any(EnviarNotificacaoRequisicao.class), eq("webhook:sem-destinatario"), any());
    }
}
