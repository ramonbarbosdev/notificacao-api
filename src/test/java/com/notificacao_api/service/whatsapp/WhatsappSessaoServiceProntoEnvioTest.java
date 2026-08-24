package com.notificacao_api.service.whatsapp;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.server.ResponseStatusException;

import com.notificacao_api.dto.whatsapp.WhatsappDiagnosticoContatoResposta;
import com.notificacao_api.repository.WhatsappSessionRepository;
import com.notificacao_api.service.TenantContextService;

@ExtendWith(MockitoExtension.class)
class WhatsappSessaoServiceProntoEnvioTest {

    @Mock
    private TenantContextService tenantContextService;

    @Mock
    private WhatsAppGatewayClient gatewayClient;

    @Mock
    private WhatsappSessionRepository whatsappSessionRepository;

    @Mock
    private WhatsappConexaoWebSocketService webSocketService;

    @Mock
    private WhatsappSessaoOperacionalService sessaoOperacionalService;

    @Mock
    private PlatformTransactionManager transactionManager;

    private WhatsappSessaoService service;

    @BeforeEach
    void setUp() {
        service = new WhatsappSessaoService(
                tenantContextService,
                gatewayClient,
                whatsappSessionRepository,
                webSocketService,
                sessaoOperacionalService,
                transactionManager,
                30L);
    }

    @Test
    void devePermitirQuandoProntoParaEnvio() {
        when(gatewayClient.diagnosticarContato(1L, "5573982229717"))
                .thenReturn(diagnostico(true, true, true, null));

        assertDoesNotThrow(() -> service.validarProntoParaEnvio(1L, "5573982229717"));
    }

    @Test
    void deveBloquearQuandoSemTcToken() {
        when(gatewayClient.diagnosticarContato(1L, "5573982229717"))
                .thenReturn(diagnostico(true, true, false, "Peca mensagem de texto."));

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.validarProntoParaEnvio(1L, "5573982229717"));

        assertEquals(403, ex.getStatusCode().value());
        assertEquals("Peca mensagem de texto.", ex.getReason());
    }

    @Test
    void deveFalharQuandoDiagnosticoNaoResponde() {
        when(gatewayClient.diagnosticarContato(1L, "5573982229717"))
                .thenReturn(diagnostico(false, false, false, "Gateway indisponivel"));

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.validarProntoParaEnvio(1L, "5573982229717"));

        assertEquals(502, ex.getStatusCode().value());
        assertEquals("Gateway indisponivel", ex.getReason());
    }

    private WhatsappDiagnosticoContatoResposta diagnostico(
            boolean sucesso,
            boolean sessaoConectada,
            boolean prontoParaEnvio,
            String orientacao) {
        return new WhatsappDiagnosticoContatoResposta(
                sucesso,
                "1",
                sucesso ? null : orientacao,
                "5573982229717",
                "5573982229717",
                sessaoConectada,
                sessaoConectada ? "CONNECTED" : "NOT_STARTED",
                prontoParaEnvio,
                prontoParaEnvio,
                orientacao,
                null,
                null,
                null,
                List.of());
    }
}
