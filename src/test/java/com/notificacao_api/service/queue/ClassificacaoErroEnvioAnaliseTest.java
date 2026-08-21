package com.notificacao_api.service.queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.notificacao_api.enums.CodigoErroEnvio;

class ClassificacaoErroEnvioAnaliseTest {

    @Test
    void classificaNumeroInvalido() {
        ClassificacaoErroEnvio.Resultado resultado = ClassificacaoErroEnvio.analisar(
                "Numero informado nao encontrado no WhatsApp (5573999999999).");

        assertEquals(CodigoErroEnvio.WHATSAPP_NUMERO_INVALIDO, resultado.codigo());
        assertEquals(ClassificacaoErroEnvio.NAO_REENVIAVEL_DESTINATARIO, resultado.classificacao());
    }

    @Test
    void classificaErroMetaCloud401() {
        ClassificacaoErroEnvio.Resultado resultado = ClassificacaoErroEnvio.analisar(
                "Nao foi possivel autenticar na WhatsApp Cloud API. Verifique o access token.");

        assertEquals(CodigoErroEnvio.WHATSAPP_META_CLOUD_ERRO, resultado.codigo());
        assertEquals(ClassificacaoErroEnvio.NAO_REENVIAVEL_CONTATO_WHATSAPP, resultado.classificacao());
    }

    @Test
    void classificaSemTcTokenAntesDoEnvio() {
        ClassificacaoErroEnvio.Resultado resultado = ClassificacaoErroEnvio.analisar(
                "WhatsApp: esta sessao linkada nao possui tctoken/conversa para 5571999729330 — nao pronto para envio.");

        assertEquals(CodigoErroEnvio.WHATSAPP_RESTRICAO_463, resultado.codigo());
        assertEquals(ClassificacaoErroEnvio.NAO_REENVIAVEL_CONTATO_WHATSAPP, resultado.classificacao());
        assertTrue(resultado.mensagemUsuario().contains("reach-out timelock"));
    }

    @Test
    void classificaRestricao463() {
        ClassificacaoErroEnvio.Resultado resultado = ClassificacaoErroEnvio.analisar(
                "error 463: account restricted or missing tctoken");

        assertEquals(CodigoErroEnvio.WHATSAPP_RESTRICAO_463, resultado.codigo());
        assertEquals(ClassificacaoErroEnvio.NAO_REENVIAVEL_CONTATO_WHATSAPP, resultado.classificacao());
        assertFalse(resultado.classificacao().contaFalhaSessao());
        assertTrue(resultado.classificacao().restricaoContatoWhatsapp());
    }

    @Test
    void classificaTimeoutEntregaComo463() {
        ClassificacaoErroEnvio.Resultado resultado = ClassificacaoErroEnvio.analisar(
                "WhatsApp nao confirmou a entrega da mensagem para 5573982229717. "
                        + "O servidor nao devolveu recibo (timed out waiting for message).");

        assertEquals(CodigoErroEnvio.WHATSAPP_RESTRICAO_463, resultado.codigo());
        assertEquals(ClassificacaoErroEnvio.NAO_REENVIAVEL_CONTATO_WHATSAPP, resultado.classificacao());
        assertFalse(resultado.classificacao().contaFalhaSessao());
        assertTrue(resultado.classificacao().restricaoContatoWhatsapp());
    }

    @Test
    void classificaUsyncVazioComo463() {
        ClassificacaoErroEnvio.Resultado resultado = ClassificacaoErroEnvio.analisar(
                "WhatsApp nao sincronizou tokens de privacidade para 5573982229717 "
                        + "(USync fetch yielded no results).");

        assertEquals(CodigoErroEnvio.WHATSAPP_RESTRICAO_463, resultado.codigo());
        assertEquals(ClassificacaoErroEnvio.NAO_REENVIAVEL_CONTATO_WHATSAPP, resultado.classificacao());
        assertFalse(resultado.classificacao().contaFalhaSessao());
        assertTrue(resultado.classificacao().restricaoContatoWhatsapp());
    }

    @Test
    void classificaErroGatewayInternoSemPausarSessao() {
        ClassificacaoErroEnvio.Resultado resultado = ClassificacaoErroEnvio.analisar(
                "tentatives is not defined");

        assertEquals(CodigoErroEnvio.GATEWAY_INDISPONIVEL, resultado.codigo());
        assertEquals(ClassificacaoErroEnvio.REENVIAVEL_INFRA, resultado.classificacao());
        assertFalse(resultado.classificacao().contaFalhaSessao());
    }

    @Test
    void classificaGatewayIndisponivelSemRecursao() {
        String mensagem = ClassificacaoErroEnvio.mensagemParaUsuario(
                "Falha generica do gateway WhatsApp");

        assertNotNull(mensagem);
        ClassificacaoErroEnvio.Resultado resultado =
                ClassificacaoErroEnvio.analisar("gateway temporariamente indisponivel");
        assertEquals(CodigoErroEnvio.GATEWAY_INDISPONIVEL, resultado.codigo());
        assertEquals(true, resultado.classificacao().reenviavel());
        assertEquals(false, resultado.classificacao().contaFalhaSessao());
    }
}
