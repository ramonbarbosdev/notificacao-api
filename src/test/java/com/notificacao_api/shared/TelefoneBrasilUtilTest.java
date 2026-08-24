package com.notificacao_api.shared;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.notificacao_api.enums.CanalNotificacao;

class TelefoneBrasilUtilTest {

    @Test
    void normalizaOnzeDigitosNacionaisComNove() {
        assertEquals("5571981180200", TelefoneBrasilUtil.normalizarCelularWhatsapp("71981180200"));
    }

    @Test
    void normalizaDezDigitosNacionaisSemNove() {
        assertEquals("5571981180200", TelefoneBrasilUtil.normalizarCelularWhatsapp("7181180200"));
    }

    @Test
    void insereNonoDigitoEmDozeDigitosComDdi() {
        assertEquals("5571981180200", TelefoneBrasilUtil.normalizarCelularWhatsapp("557181180200"));
    }

    @Test
    void naoDuplicaNonoDigitoQuandoJaExisteNaPosicaoQuatro() {
        assertEquals("5571981180200", TelefoneBrasilUtil.normalizarCelularWhatsapp("5571981180200"));
    }

    @Test
    void removeMascaraAntesDeNormalizar() {
        assertEquals(
                "5571981180200",
                TelefoneBrasilUtil.normalizarDestino(
                        CanalNotificacao.WHATSAPP,
                        "+55 (71) 98118-0200"));
    }

    @Test
    void corrigeNonoDigitoDeslocadoEmDozeDigitosComDdi() {
        assertEquals("5571981180200", TelefoneBrasilUtil.normalizarCelularWhatsapp("557191180200"));
    }

    @Test
    void naoCorrompeCelularComLocalIniciandoEm99() {
        assertEquals("557199729330", TelefoneBrasilUtil.normalizarCelularWhatsapp("557199729330"));
    }

    @Test
    void identificaCelularComNonoDigito() {
        assertTrue(TelefoneBrasilUtil.celularBrasilComNonoDigito("5571981180200"));
    }

    @Test
    void detectaNomeQuePareceTelefone() {
        assertTrue(TelefoneBrasilUtil.nomePareceTelefone("5573982229717", "5573982229717"));
        assertTrue(TelefoneBrasilUtil.nomePareceTelefone("(73) 98222-9717", "5573982229717"));
    }

    @Test
    void preservaNomeRealDoContato() {
        assertEquals(
                "Maria Silva",
                TelefoneBrasilUtil.resolverNomeContatoWhatsapp("Maria Silva", "5573982229717"));
        assertEquals(null, TelefoneBrasilUtil.resolverNomeContatoWhatsapp("5573982229717", "5573982229717"));
    }
}
