package com.notificacao_api.service.whatsapp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.notificacao_api.dto.whatsapp.TcTokenAudienciaLinhaResponse;
import com.notificacao_api.enums.TcTokenAudienciaOrigem;
import com.notificacao_api.enums.TcTokenAudienciaSituacao;

class TcTokenConfirmacaoMensagemSupportTest {

    private static final String TELEFONE = "5571991180200";

    @Test
    void resolverMensagem_usaOverrideInformado() {
        TcTokenAudienciaLinhaResponse linha = linhaGithub(5.0);
        assertEquals(
                "Custom",
                TcTokenConfirmacaoMensagemSupport.resolverMensagem("Custom", "tpl {login}", linha, false));
    }

    @Test
    void resolverMensagem_templateComPlaceholders() {
        TcTokenAudienciaLinhaResponse linha = linhaGithub(5.3);
        String msg = TcTokenConfirmacaoMensagemSupport.resolverMensagem(
                null, "Ola {login}! Faltam {diasRestantes} dias.", linha, false);
        assertEquals("Ola @dev-user! Faltam 5 dias.", msg);
    }

    @Test
    void resolverMensagem_templateBlankUsaSistema() {
        TcTokenAudienciaLinhaResponse linha = linhaGithub(5.0);
        String msg = TcTokenConfirmacaoMensagemSupport.resolverMensagem(null, "   ", linha, false);
        assertTrue(msg.contains("@dev-user"));
        assertTrue(msg.contains("5 dia(s)"));
    }

    @Test
    void resolverMensagem_modoTestePrefixa() {
        TcTokenAudienciaLinhaResponse linha = linhaGithub(5.0);
        String msg = TcTokenConfirmacaoMensagemSupport.resolverMensagem("Oi", null, linha, true);
        assertEquals("[TESTE ADMIN] Oi", msg);
    }

    @Test
    void resolverDiasPlaceholder_nullRetornaTraco() {
        TcTokenAudienciaLinhaResponse linha = linhaGithub(null);
        assertEquals(
                "—",
                TcTokenConfirmacaoMensagemSupport.resolverDiasPlaceholder(linha));
    }

    private static TcTokenAudienciaLinhaResponse linhaGithub(Double expiraEmDias) {
        return new TcTokenAudienciaLinhaResponse(
                TELEFONE,
                "********0200",
                "@dev-user",
                List.of(TcTokenAudienciaOrigem.GITHUB),
                23.0,
                expiraEmDias,
                TcTokenAudienciaSituacao.PROXIMO_EXPIRAR,
                "Proximo",
                false,
                false,
                null,
                true);
    }
}
