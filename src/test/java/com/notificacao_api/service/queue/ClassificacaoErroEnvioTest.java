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
    void classificaErro463ComoContatoWhatsappSemPausarSessao() {
        ClassificacaoErroEnvio classificacao =
                ClassificacaoErroEnvio.classificar(
                        "WhatsApp bloqueou o envio para este contato (restricao 463)");

        assertEquals(ClassificacaoErroEnvio.NAO_REENVIAVEL_CONTATO_WHATSAPP, classificacao);
        assertFalse(classificacao.reenviavel());
        assertFalse(classificacao.contaFalhaSessao());
        assertTrue(classificacao.restricaoContatoWhatsapp());
        assertFalse(classificacao.bloqueioContatoImediato());
    }

    @Test
    void classificaNumeroInexistenteComoDestinatarioInvalido() {
        ClassificacaoErroEnvio classificacao =
                ClassificacaoErroEnvio.classificar(
                        "Numero informado nao encontrado no WhatsApp (5573999999999)");

        assertEquals(ClassificacaoErroEnvio.NAO_REENVIAVEL_DESTINATARIO, classificacao);
        assertFalse(classificacao.reenviavel());
        assertTrue(classificacao.bloqueioContatoImediato());
    }
}
