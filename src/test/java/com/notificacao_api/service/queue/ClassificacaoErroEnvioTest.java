package com.notificacao_api.service.queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ClassificacaoErroEnvioTest {

    @Test
    void classificaErroDeDestinatarioComoNaoReenviavelEBloqueioImediato() {
        ClassificacaoErroEnvio classificacao =
                ClassificacaoErroEnvio.classificar("Numero informado nao encontrado no WhatsApp");

        assertEquals(ClassificacaoErroEnvio.NAO_REENVIAVEL_DESTINATARIO, classificacao);
        assertFalse(classificacao.reenviavel());
        assertFalse(classificacao.contaFalhaSessao());
        assertTrue(classificacao.bloqueioContatoImediato());
    }

    @Test
    void classificaDesconexaoComoInfraSemContarFalhaDeSessao() {
        ClassificacaoErroEnvio classificacao =
                ClassificacaoErroEnvio.classificar("WhatsApp nao conectado para a organizacao");

        assertEquals(ClassificacaoErroEnvio.NAO_REENVIAVEL_INFRA, classificacao);
        assertFalse(classificacao.reenviavel());
        assertFalse(classificacao.contaFalhaSessao());
    }

    @Test
    void classificaTimeoutComoReenviavelEContaFalhaDeSessao() {
        ClassificacaoErroEnvio classificacao =
                ClassificacaoErroEnvio.classificar("Gateway timeout ao enviar mensagem");

        assertEquals(ClassificacaoErroEnvio.REENVIAVEL, classificacao);
        assertTrue(classificacao.reenviavel());
        assertTrue(classificacao.contaFalhaSessao());
    }
}
