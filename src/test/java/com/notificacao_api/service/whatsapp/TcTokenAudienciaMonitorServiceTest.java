package com.notificacao_api.service.whatsapp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.notificacao_api.dto.whatsapp.TcTokenAudienciaScanResponse;
import com.notificacao_api.enums.CanalNotificacao;
import com.notificacao_api.enums.StatusNotificacao;
import com.notificacao_api.enums.TcTokenAudienciaOrigem;
import com.notificacao_api.enums.TcTokenAudienciaSituacao;
import com.notificacao_api.enums.WhatsappSessionStatus;
import com.notificacao_api.model.OrganizacaoGithubResponsavel;
import com.notificacao_api.model.WhatsappSession;
import com.notificacao_api.repository.NotificacaoRepository;
import com.notificacao_api.repository.OrganizacaoGithubResponsavelRepository;
import com.notificacao_api.repository.WhatsappSessionRepository;

@ExtendWith(MockitoExtension.class)
class TcTokenAudienciaMonitorServiceTest {

    private static final Long ID_ORG = 1L;
    private static final String TEL_GITHUB = "5571991180200";
    private static final String TEL_FILA = "5571981180200";

    @Mock
    private OrganizacaoGithubResponsavelRepository githubResponsavelRepository;

    @Mock
    private NotificacaoRepository notificacaoRepository;

    @Mock
    private WhatsappSessionRepository whatsappSessionRepository;

    @Mock
    private WhatsAppGatewayClient gatewayClient;

    private TcTokenAudienciaMonitorService service;

    @BeforeEach
    void setUp() {
        service = new TcTokenAudienciaMonitorService(
                githubResponsavelRepository,
                notificacaoRepository,
                whatsappSessionRepository,
                gatewayClient,
                28,
                7,
                60,
                30,
                60,
                300,
                false);
    }

    @Test
    void executarScan_unificaGithubEFila_eOrdenaProximoExpirarPrimeiro() {
        OrganizacaoGithubResponsavel responsavel = new OrganizacaoGithubResponsavel();
        responsavel.setAtivo(true);
        responsavel.setDsGithubLogin("dev-user");
        responsavel.setNuWhatsapp(TEL_GITHUB);

        when(githubResponsavelRepository.findByIdOrganizacaoOrderByDtAtualizacaoDesc(ID_ORG))
                .thenReturn(List.of(responsavel));
        when(notificacaoRepository.listarDestinatariosDistintosDesde(
                        eq(ID_ORG), eq(CanalNotificacao.WHATSAPP), any(LocalDateTime.class)))
                .thenReturn(List.of(TEL_FILA));
        when(notificacaoRepository.listarDestinatariosDistintosEnviadosDesde(
                        eq(ID_ORG),
                        eq(CanalNotificacao.WHATSAPP),
                        any(LocalDateTime.class),
                        eq(List.of(
                                StatusNotificacao.ENVIADA,
                                StatusNotificacao.ENTREGUE,
                                StatusNotificacao.LIDA))))
                .thenReturn(List.of(TEL_FILA));

        when(gatewayClient.consultarTcTokenAudiencia(eq(ID_ORG), any()))
                .thenReturn(Map.of(
                        "sucesso", true,
                        "linhas",
                        List.of(
                                linhaGateway(TEL_GITHUB, 22.0, 6.0, true, false, true),
                                linhaGateway(TEL_FILA, 5.0, 23.0, true, false, true))));

        TcTokenAudienciaScanResponse scan = service.executarScan(ID_ORG);

        assertEquals(2, scan.linhas().size());
        assertEquals(TEL_GITHUB, scan.linhas().get(0).telefone());
        assertEquals(TcTokenAudienciaSituacao.PROXIMO_EXPIRAR, scan.linhas().get(0).situacao());
        assertEquals(TEL_FILA, scan.linhas().get(1).telefone());
        assertEquals(TcTokenAudienciaSituacao.OK, scan.linhas().get(1).situacao());

        assertTrue(scan.linhas().stream().anyMatch(l -> l.origens().contains(TcTokenAudienciaOrigem.GITHUB)));
        assertTrue(scan.linhas().stream().anyMatch(l -> l.origens().contains(TcTokenAudienciaOrigem.FILA)));
        assertEquals(1, scan.kpis().proximoExpirar());
        assertTrue(scan.varreduraGatewayCompleta());
    }

    @Test
    void obter_semCache_naoConsultaGateway() {
        OrganizacaoGithubResponsavel responsavel = new OrganizacaoGithubResponsavel();
        responsavel.setAtivo(true);
        responsavel.setDsGithubLogin("dev-user");
        responsavel.setNuWhatsapp(TEL_GITHUB);

        when(githubResponsavelRepository.findByIdOrganizacaoOrderByDtAtualizacaoDesc(ID_ORG))
                .thenReturn(List.of(responsavel));
        when(notificacaoRepository.listarDestinatariosDistintosDesde(
                        eq(ID_ORG), eq(CanalNotificacao.WHATSAPP), any(LocalDateTime.class)))
                .thenReturn(List.of());
        when(whatsappSessionRepository.findByIdOrganizacao(ID_ORG)).thenReturn(Optional.empty());

        TcTokenAudienciaScanResponse scan = service.obter(ID_ORG, false);

        assertEquals(1, scan.linhas().size());
        assertFalse(scan.varreduraGatewayCompleta());
        verify(gatewayClient, never()).consultarTcTokenAudiencia(any(), any());
        verify(gatewayClient, never()).obterStatus(any());
    }

    @Test
    void obter_sessaoConectada_enriqueceAutomaticamente() {
        OrganizacaoGithubResponsavel responsavel = new OrganizacaoGithubResponsavel();
        responsavel.setAtivo(true);
        responsavel.setDsGithubLogin("dev-user");
        responsavel.setNuWhatsapp(TEL_GITHUB);

        WhatsappSession sessao = new WhatsappSession();
        sessao.setTpStatus(WhatsappSessionStatus.CONECTADO);

        when(githubResponsavelRepository.findByIdOrganizacaoOrderByDtAtualizacaoDesc(ID_ORG))
                .thenReturn(List.of(responsavel));
        when(notificacaoRepository.listarDestinatariosDistintosDesde(
                        eq(ID_ORG), eq(CanalNotificacao.WHATSAPP), any(LocalDateTime.class)))
                .thenReturn(List.of());
        when(notificacaoRepository.listarDestinatariosDistintosEnviadosDesde(
                        eq(ID_ORG),
                        eq(CanalNotificacao.WHATSAPP),
                        any(LocalDateTime.class),
                        any()))
                .thenReturn(List.of());
        when(whatsappSessionRepository.findByIdOrganizacao(ID_ORG)).thenReturn(Optional.of(sessao));
        when(gatewayClient.consultarTcTokenAudiencia(eq(ID_ORG), any()))
                .thenReturn(Map.of(
                        "sucesso", true,
                        "linhas",
                        List.of(linhaGateway(TEL_GITHUB, 10.0, 18.0, true, false, true))));

        TcTokenAudienciaScanResponse scan = service.obter(ID_ORG, false);

        assertTrue(scan.varreduraGatewayCompleta());
        assertEquals(18.0, scan.linhas().get(0).expiraEmDias());
        verify(gatewayClient).consultarTcTokenAudiencia(eq(ID_ORG), any());
    }

    private static Map<String, Object> linhaGateway(
            String telefone,
            double idadeDias,
            double expiraEmDias,
            boolean tokenPresente,
            boolean expirado,
            boolean prontoParaEnvio) {
        return Map.of(
                "telefone", telefone,
                "idadeDias", idadeDias,
                "expiraEmDias", expiraEmDias,
                "tokenPresente", tokenPresente,
                "expirado", expirado,
                "prontoParaEnvio", prontoParaEnvio);
    }
}
