package com.notificacao_api.service.whatsapp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import com.notificacao_api.dto.notificacao.EnviarNotificacaoRequisicao;
import com.notificacao_api.dto.notificacao.EnviarNotificacaoResposta;
import com.notificacao_api.dto.whatsapp.TcTokenAudienciaLinhaResponse;
import com.notificacao_api.dto.whatsapp.TcTokenAudienciaPedirConfirmacaoRequest;
import com.notificacao_api.enums.CanalNotificacao;
import com.notificacao_api.enums.StatusNotificacao;
import com.notificacao_api.enums.TcTokenAudienciaOrigem;
import com.notificacao_api.enums.TcTokenAudienciaSituacao;
import com.notificacao_api.exception.EnvioMensagensDesabilitadoException;
import com.notificacao_api.model.OrganizacaoGithubResponsavel;
import com.notificacao_api.repository.NotificacaoRepository;
import com.notificacao_api.repository.OrganizacaoGithubResponsavelRepository;
import com.notificacao_api.service.AuditoriaEventoService;
import com.notificacao_api.service.NotificacaoService;
import com.notificacao_api.service.OrganizacaoConfiguracaoService;
import com.notificacao_api.service.TenantContextService;
import com.notificacao_api.service.queue.ProtecaoNotificacaoService;

@ExtendWith(MockitoExtension.class)
class TcTokenAudienciaReativacaoServiceTest {

    private static final Long ID_ORG = 9L;
    private static final String TELEFONE = "5571991180200";

    @Mock
    private TenantContextService tenantContextService;

    @Mock
    private TcTokenAudienciaMonitorService monitorService;

    @Mock
    private OrganizacaoConfiguracaoService organizacaoConfiguracaoService;

    @Mock
    private NotificacaoService notificacaoService;

    @Mock
    private NotificacaoRepository notificacaoRepository;

    @Mock
    private ProtecaoNotificacaoService protecaoNotificacaoService;

    @Mock
    private AuditoriaEventoService auditoriaEventoService;

    @Mock
    private OrganizacaoGithubResponsavelRepository githubResponsavelRepository;

    private TcTokenAudienciaReativacaoService service;

    @BeforeEach
    void setUp() {
        service = new TcTokenAudienciaReativacaoService(
                tenantContextService,
                monitorService,
                organizacaoConfiguracaoService,
                githubResponsavelRepository,
                notificacaoService,
                notificacaoRepository,
                protecaoNotificacaoService,
                auditoriaEventoService,
                7);
    }

    private void stubTemplatePadrao() {
        when(organizacaoConfiguracaoService.tctokenConfirmacaoMensagemPadrao(ID_ORG)).thenReturn(null);
    }

    private void stubTenant() {
        when(tenantContextService.idOrganizacaoObrigatoria()).thenReturn(ID_ORG);
    }

    private void stubTenantEProtecao() {
        stubTenant();
        when(protecaoNotificacaoService.agora()).thenReturn(LocalDateTime.of(2026, 3, 1, 12, 0));
    }

    @Test
    void pedirConfirmacao_rejeitaTelefoneForaDaAudienciaEmUso() {
        stubTenant();
        when(monitorService.telefoneNaAudienciaEmUso(ID_ORG, TELEFONE)).thenReturn(false);

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.pedirConfirmacao(new TcTokenAudienciaPedirConfirmacaoRequest(TELEFONE, null)));

        assertEquals(400, ex.getStatusCode().value());
        verify(notificacaoService, never()).enviar(any());
    }

    @Test
    void pedirConfirmacao_rejeitaQuandoEnvioDesabilitado() {
        stubTenant();
        when(monitorService.telefoneNaAudienciaEmUso(ID_ORG, TELEFONE)).thenReturn(true);
        when(monitorService.buscarLinhaNoCache(ID_ORG, TELEFONE)).thenReturn(Optional.of(linhaElegivel()));
        doThrow(new EnvioMensagensDesabilitadoException())
                .when(organizacaoConfiguracaoService)
                .validarEnvioMensagensHabilitado(ID_ORG);

        assertThrows(
                EnvioMensagensDesabilitadoException.class,
                () -> service.pedirConfirmacao(new TcTokenAudienciaPedirConfirmacaoRequest(TELEFONE, null)));

        verify(notificacaoService, never()).enviar(any());
    }

    @Test
    void pedirConfirmacao_rejeitaDedupeRecente() {
        stubTenantEProtecao();
        when(monitorService.telefoneNaAudienciaEmUso(ID_ORG, TELEFONE)).thenReturn(true);
        when(monitorService.buscarLinhaNoCache(ID_ORG, TELEFONE)).thenReturn(Optional.of(linhaElegivel()));
        String referencia = TcTokenAudienciaReativacaoService.referenciaConfirmacao(ID_ORG, TELEFONE);
        when(notificacaoRepository.existsByIdOrganizacaoAndCanalAndDestinatarioAndVariaveisTemplateAndDtCriacaoAfterAndStatusIn(
                        eq(ID_ORG),
                        eq(CanalNotificacao.WHATSAPP),
                        eq(TELEFONE),
                        eq(TcTokenAudienciaReativacaoService.MARCADOR_VARIAVEIS_CONFIRMACAO + "|" + referencia),
                        any(),
                        any()))
                .thenReturn(true);

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.pedirConfirmacao(new TcTokenAudienciaPedirConfirmacaoRequest(TELEFONE, null)));

        assertEquals(429, ex.getStatusCode().value());
        verify(notificacaoService, never()).enviar(any());
    }

    @Test
    void pedirConfirmacao_enfileiraComMensagemDefaultPersonalizada() {
        stubTenantEProtecao();
        stubTemplatePadrao();
        when(monitorService.telefoneNaAudienciaEmUso(ID_ORG, TELEFONE)).thenReturn(true);
        when(monitorService.buscarLinhaNoCache(ID_ORG, TELEFONE)).thenReturn(Optional.of(linhaElegivel()));
        when(notificacaoRepository.existsByIdOrganizacaoAndCanalAndDestinatarioAndVariaveisTemplateAndDtCriacaoAfterAndStatusIn(
                        any(), any(), any(), any(), any(), any()))
                .thenReturn(false);
        when(notificacaoService.enviar(any()))
                .thenReturn(new EnviarNotificacaoResposta(
                        true,
                        42L,
                        CanalNotificacao.WHATSAPP,
                        StatusNotificacao.PENDENTE,
                        null,
                        null,
                        null,
                        0,
                        3,
                        null,
                        null,
                        null));

        service.pedirConfirmacao(new TcTokenAudienciaPedirConfirmacaoRequest(TELEFONE, null));

        ArgumentCaptor<EnviarNotificacaoRequisicao> captor = ArgumentCaptor.forClass(EnviarNotificacaoRequisicao.class);
        verify(notificacaoService).enviar(captor.capture());
        EnviarNotificacaoRequisicao req = captor.getValue();
        assertTrue(req.mensagem().contains("@dev-user"));
        assertTrue(req.mensagem().contains("5 dia(s)"));
        assertEquals(TcTokenAudienciaReativacaoService.referenciaConfirmacao(ID_ORG, TELEFONE), req.referenciaExterna());
    }

    @Test
    void montarMensagem_usaTextoInformadoQuandoPresente() {
        TcTokenAudienciaLinhaResponse linha = linhaElegivel();
        assertEquals("Minha mensagem", TcTokenAudienciaReativacaoService.montarMensagem("Minha mensagem", linha));
    }

    @Test
    void linhaElegivelConfirmacaoAutomatica_requerOptInGithub() {
        when(githubResponsavelRepository.findByIdOrganizacaoAndNuWhatsapp(ID_ORG, TELEFONE))
                .thenReturn(Optional.empty());
        assertTrue(!service.linhaElegivelConfirmacaoAutomatica(ID_ORG, linhaElegivel(), 7));
    }

    @Test
    void linhaElegivelConfirmacaoAutomatica_respeitaLimiarDias() {
        stubGithubOptIn();
        assertTrue(service.linhaElegivelConfirmacaoAutomatica(ID_ORG, linhaElegivel(), 7));
        assertTrue(!service.linhaElegivelConfirmacaoAutomatica(ID_ORG, linhaElegivel(), 4));
    }

    private void stubGithubOptIn() {
        OrganizacaoGithubResponsavel responsavel = new OrganizacaoGithubResponsavel();
        responsavel.setAtivo(true);
        responsavel.setDsGithubLogin("dev-user");
        when(githubResponsavelRepository.findByIdOrganizacaoAndNuWhatsapp(ID_ORG, TELEFONE))
                .thenReturn(Optional.of(responsavel));
    }

    @Test
    void pedirConfirmacao_modoTeste_permiteTokenOk() {
        stubTenant();
        stubTemplatePadrao();
        when(monitorService.telefoneNaAudiencia(ID_ORG, TELEFONE)).thenReturn(true);
        when(monitorService.buscarLinhaNoCache(ID_ORG, TELEFONE)).thenReturn(Optional.of(linhaOk()));
        when(notificacaoService.enviar(any()))
                .thenReturn(new EnviarNotificacaoResposta(
                        true,
                        99L,
                        CanalNotificacao.WHATSAPP,
                        StatusNotificacao.PENDENTE,
                        null,
                        null,
                        null,
                        0,
                        3,
                        null,
                        null,
                        null));

        var req = new TcTokenAudienciaPedirConfirmacaoRequest(TELEFONE, "Oi teste", true);
        service.pedirConfirmacao(req);

        verify(notificacaoRepository, never())
                .existsByIdOrganizacaoAndCanalAndDestinatarioAndVariaveisTemplateAndDtCriacaoAfterAndStatusIn(
                        any(), any(), any(), any(), any(), any());
        ArgumentCaptor<EnviarNotificacaoRequisicao> captor = ArgumentCaptor.forClass(EnviarNotificacaoRequisicao.class);
        verify(notificacaoService).enviar(captor.capture());
        assertTrue(captor.getValue().mensagem().startsWith("[TESTE ADMIN]"));
    }

    private static TcTokenAudienciaLinhaResponse linhaOk() {
        return new TcTokenAudienciaLinhaResponse(
                TELEFONE,
                "********0200",
                "@dev-user",
                List.of(TcTokenAudienciaOrigem.GITHUB),
                5.0,
                20.0,
                TcTokenAudienciaSituacao.OK,
                "OK",
                true,
                false,
                null,
                true);
    }

    private static TcTokenAudienciaLinhaResponse linhaElegivel() {
        return new TcTokenAudienciaLinhaResponse(
                TELEFONE,
                "********0200",
                "@dev-user",
                List.of(TcTokenAudienciaOrigem.GITHUB),
                23.0,
                5.0,
                TcTokenAudienciaSituacao.PROXIMO_EXPIRAR,
                "Proximo de expirar",
                false,
                false,
                null,
                true);
    }
}
