package com.notificacao_api.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.notificacao_api.integration.asaas.dto.AsaasPaymentResponse;
import com.notificacao_api.integration.asaas.dto.AsaasWebhookPayload;
import com.notificacao_api.model.Organizacao;
import com.notificacao_api.model.OrganizacaoAssinatura;
import com.notificacao_api.repository.OrganizacaoAssinaturaRepository;
import com.notificacao_api.repository.OrganizacaoRepository;
import com.notificacao_api.repository.PagamentoWebhookProcessadoRepository;

@ExtendWith(MockitoExtension.class)
class AsaasWebhookServiceTest {

    @Mock
    private PagamentoWebhookProcessadoRepository webhookProcessadoRepository;
    @Mock
    private OrganizacaoCobrancaService cobrancaService;
    @Mock
    private AssinaturaService assinaturaService;
    @Mock
    private OrganizacaoAssinaturaRepository assinaturaRepository;
    @Mock
    private OrganizacaoRepository organizacaoRepository;

    @InjectMocks
    private AsaasWebhookService webhookService;

    @Test
    void deveIgnorarEventoDuplicado() {
        AsaasWebhookPayload payload = new AsaasWebhookPayload(
                "evt_1",
                "PAYMENT_RECEIVED",
                pagamento("pay_1", "cus_1", "sub_1"),
                null);

        when(webhookProcessadoRepository.existsByIdEventoAsaas("evt_1")).thenReturn(true);

        webhookService.processar(payload);

        verify(cobrancaService, never()).registrarOuAtualizar(any(), any());
    }

    @Test
    void deveAtivarAssinaturaQuandoPagamentoRecebido() {
        AsaasWebhookPayload payload = new AsaasWebhookPayload(
                "evt_2",
                "PAYMENT_RECEIVED",
                pagamento("pay_2", "cus_2", "sub_2"),
                null);

        Organizacao org = new Organizacao();
        org.setIdOrganizacao(10L);
        org.setIdClienteAsaas("cus_2");

        OrganizacaoAssinatura assinatura = new OrganizacaoAssinatura();
        assinatura.setIdOrganizacao(10L);
        assinatura.setIdPlano(3L);

        when(webhookProcessadoRepository.existsByIdEventoAsaas("evt_2")).thenReturn(false);
        when(organizacaoRepository.findByIdClienteAsaas("cus_2")).thenReturn(Optional.of(org));
        when(assinaturaRepository.findByIdOrganizacao(10L)).thenReturn(Optional.of(assinatura));

        webhookService.processar(payload);

        verify(cobrancaService).registrarOuAtualizar(eq(10L), any(AsaasPaymentResponse.class));
        verify(assinaturaService).ativarPorPagamento(10L, 3L);
        verify(webhookProcessadoRepository).save(any());
    }

    private AsaasPaymentResponse pagamento(String id, String customer, String subscription) {
        return new AsaasPaymentResponse(
                id,
                customer,
                subscription,
                "RECEIVED",
                "PIX",
                new BigDecimal("49.90"),
                "2026-04-01",
                "2026-04-01",
                "000201",
                "base64",
                "https://invoice");
    }
}
