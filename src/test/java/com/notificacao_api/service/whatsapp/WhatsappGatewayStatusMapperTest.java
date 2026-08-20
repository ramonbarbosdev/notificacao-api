package com.notificacao_api.service.whatsapp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WhatsappGatewayStatusMapperTest {

    @Test
    void deveMapearStatusDoGatewayParaEnumDaApi() {
        assertEquals("AGUARDANDO_QR", WhatsappGatewayStatusMapper.normalizar("PENDING_QR"));
        assertEquals("CONECTADO", WhatsappGatewayStatusMapper.normalizar("CONNECTED"));
        assertEquals("CONECTANDO", WhatsappGatewayStatusMapper.normalizar("CONNECTING"));
    }

    @Test
    void deveReconhecerTentativaEmAndamento() {
        assertTrue(WhatsappGatewayStatusMapper.emAndamento("PENDING_QR"));
        assertTrue(WhatsappGatewayStatusMapper.emAndamento("AGUARDANDO_QR"));
        assertFalse(WhatsappGatewayStatusMapper.emAndamento("CONNECTED"));
        assertFalse(WhatsappGatewayStatusMapper.emAndamento("CONECTADO"));
    }
}
