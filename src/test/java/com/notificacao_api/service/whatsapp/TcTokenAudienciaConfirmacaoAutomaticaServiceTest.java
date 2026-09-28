package com.notificacao_api.service.whatsapp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.notificacao_api.dto.notificacao.EnviarNotificacaoResposta;
import com.notificacao_api.dto.whatsapp.TcTokenAudienciaLinhaResponse;
import com.notificacao_api.dto.whatsapp.TcTokenAudienciaScanResponse;
import com.notificacao_api.enums.CanalNotificacao;
import com.notificacao_api.enums.StatusNotificacao;
import com.notificacao_api.enums.TcTokenAudienciaOrigem;
import com.notificacao_api.enums.TcTokenAudienciaSituacao;
import com.notificacao_api.service.OrganizacaoConfiguracaoService;

@ExtendWith(MockitoExtension.class)
class TcTokenAudienciaConfirmacaoAutomaticaServiceTest {

    private static final Long ID_ORG = 3L;

    @Mock
    private TcTokenAudienciaMonitorService monitorService;

    @Mock
    private TcTokenAudienciaReativacaoService reativacaoService;

    @Mock
    private OrganizacaoConfiguracaoService organizacaoConfiguracaoService;

    private TcTokenAudienciaConfirmacaoAutomaticaService service;

    @BeforeEach
    void setUp() {
        service = new TcTokenAudienciaConfirmacaoAutomaticaService(
                monitorService, reativacaoService, organizacaoConfiguracaoService, 2);
    }

    @Test
    void processarOrganizacao_ignoraQuandoMotorDesligado() {
        when(organizacaoConfiguracaoService.tctokenConfirmacaoAutomaticaHabilitado(ID_ORG)).thenReturn(false);

        var resultado = service.processarOrganizacao(ID_ORG);

        assertTrue(resultado.ignoradoOrganizacao());
        assertEquals("Motor desligado", resultado.motivoIgnorado());
        verify(monitorService, never()).executarScan(any());
    }

    @Test
    void processarOrganizacao_respeitaLimitePorExecucao() {
        when(organizacaoConfiguracaoService.tctokenConfirmacaoAutomaticaHabilitado(ID_ORG)).thenReturn(true);
        when(organizacaoConfiguracaoService.envioMensagensHabilitado(ID_ORG)).thenReturn(true);
        when(organizacaoConfiguracaoService.tctokenConfirmacaoAutomaticaDiasAntes(ID_ORG)).thenReturn(7);

        TcTokenAudienciaLinhaResponse l1 = linha(1, 5.0);
        TcTokenAudienciaLinhaResponse l2 = linha(2, 4.0);
        TcTokenAudienciaLinhaResponse l3 = linha(3, 3.0);
        when(monitorService.executarScan(ID_ORG))
                .thenReturn(scan(List.of(l1, l2, l3)));

        when(reativacaoService.linhaElegivelConfirmacaoAutomatica(eq(ID_ORG), any(), eq(7)))
                .thenReturn(true);
        when(reativacaoService.pedirConfirmacaoAutomatica(eq(ID_ORG), any()))
                .thenReturn(respostaOk());

        var resultado = service.processarOrganizacao(ID_ORG);

        assertEquals(2, resultado.enfileirados());
        verify(reativacaoService, times(2)).pedirConfirmacaoAutomatica(eq(ID_ORG), any());
    }

    private static EnviarNotificacaoResposta respostaOk() {
        return new EnviarNotificacaoResposta(
                true,
                1L,
                CanalNotificacao.WHATSAPP,
                StatusNotificacao.PENDENTE,
                null,
                null,
                null,
                0,
                3,
                null,
                null,
                null);
    }

    private static TcTokenAudienciaScanResponse scan(List<TcTokenAudienciaLinhaResponse> linhas) {
        return new TcTokenAudienciaScanResponse(
                true,
                null,
                ID_ORG,
                null,
                true,
                28,
                7,
                60,
                true,
                null,
                linhas);
    }

    private static TcTokenAudienciaLinhaResponse linha(int suffix, double expiraEmDias) {
        String tel = "557199118020" + suffix;
        return new TcTokenAudienciaLinhaResponse(
                tel,
                "********020" + suffix,
                "@u" + suffix,
                List.of(TcTokenAudienciaOrigem.GITHUB),
                10.0,
                expiraEmDias,
                TcTokenAudienciaSituacao.PROXIMO_EXPIRAR,
                "Proximo",
                false,
                false,
                null,
                true);
    }
}
