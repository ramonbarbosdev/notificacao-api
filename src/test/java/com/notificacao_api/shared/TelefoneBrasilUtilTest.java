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
    void identificaCelularComNonoDigito() {
        assertTrue(TelefoneBrasilUtil.celularBrasilComNonoDigito("5571981180200"));
    }
}
