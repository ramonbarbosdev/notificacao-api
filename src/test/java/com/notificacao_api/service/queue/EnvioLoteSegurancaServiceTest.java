package com.notificacao_api.service.queue;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import com.notificacao_api.config.PropriedadesProtecaoNotificacao;
import com.notificacao_api.dto.notificacao.EnviarNotificacaoLoteItemRequisicao;
import com.notificacao_api.dto.notificacao.EnviarNotificacaoLoteRequisicao;
import com.notificacao_api.enums.CanalNotificacao;

class EnvioLoteSegurancaServiceTest {

    private EnvioLoteSegurancaService service;

    @BeforeEach
    void setUp() {
        PropriedadesProtecaoNotificacao propriedades = new PropriedadesProtecaoNotificacao(
                5,
                60,
                300,
                15,
                15,
                LocalTime.of(8, 0),
                LocalTime.of(18, 0),
                "America/Bahia",
                3,
                900,
                1800,
                5,
                30,
                2,
                60,
                1,
                5000,
                3,
                10);
        service = new EnvioLoteSegurancaService(propriedades);
    }

    @Test
    void deveRejeitarLoteAcimaDoLimiteOperacional() {
        EnviarNotificacaoLoteRequisicao requisicao = loteComQuantidade(4);

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.validarEstruturaLote(requisicao));

        assertEquals(400, ex.getStatusCode().value());
        assertEquals(
                "Lote excede o limite operacional de 3 mensagens.",
                ex.getReason());
    }

    @Test
    void deveRejeitarCanalDiferenteDeWhatsapp() {
        EnviarNotificacaoLoteRequisicao requisicao = new EnviarNotificacaoLoteRequisicao(
                CanalNotificacao.EMAIL,
                List.of(item("5571994686855", "msg")));

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.validarEstruturaLote(requisicao));

        assertEquals(400, ex.getStatusCode().value());
        assertEquals(
                "Envio em lote disponivel apenas para WHATSAPP.",
                ex.getReason());
    }

    @Test
    void deveRejeitarDuplicataInterna() {
        EnviarNotificacaoLoteRequisicao requisicao = new EnviarNotificacaoLoteRequisicao(
                CanalNotificacao.WHATSAPP,
                List.of(
                        item("5571994686855", "mesma mensagem"),
                        item("5571994686855", "mesma mensagem")));

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.validarEstruturaLote(requisicao));

        assertEquals(400, ex.getStatusCode().value());
        assertEquals(
                "Lote contem mensagens duplicadas para o mesmo destinatario e conteudo.",
                ex.getReason());
    }

    @Test
    void deveRejeitarReferenciaExternaDuplicada() {
        EnviarNotificacaoLoteRequisicao requisicao = new EnviarNotificacaoLoteRequisicao(
                CanalNotificacao.WHATSAPP,
                List.of(
                        itemComReferencia("5571994686855", "msg 1", "pedido-1"),
                        itemComReferencia("5571981180200", "msg 2", "pedido-1")));

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.validarEstruturaLote(requisicao));

        assertEquals(400, ex.getStatusCode().value());
        assertEquals(
                "Lote contem referenciaExterna duplicada: pedido-1.",
                ex.getReason());
    }

    @Test
    void deveAceitarLoteValido() {
        EnviarNotificacaoLoteRequisicao requisicao = new EnviarNotificacaoLoteRequisicao(
                CanalNotificacao.WHATSAPP,
                List.of(
                        itemComReferencia("5571994686855", "msg 1", "pedido-1"),
                        itemComReferencia("5571981180200", "msg 2", "pedido-2")));

        assertDoesNotThrow(() -> service.validarEstruturaLote(requisicao));
    }

    private EnviarNotificacaoLoteRequisicao loteComQuantidade(int quantidade) {
        List<EnviarNotificacaoLoteItemRequisicao> mensagens = java.util.stream.IntStream
                .range(0, quantidade)
                .mapToObj(i -> item("557199468685" + i, "msg " + i))
                .toList();
        return new EnviarNotificacaoLoteRequisicao(CanalNotificacao.WHATSAPP, mensagens);
    }

    private EnviarNotificacaoLoteItemRequisicao item(String destinatario, String mensagem) {
        return new EnviarNotificacaoLoteItemRequisicao(destinatario, null, mensagem, null);
    }

    private EnviarNotificacaoLoteItemRequisicao itemComReferencia(
            String destinatario,
            String mensagem,
            String referenciaExterna) {
        return new EnviarNotificacaoLoteItemRequisicao(
                destinatario,
                null,
                mensagem,
                referenciaExterna);
    }
}
