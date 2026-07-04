package com.notificacao_api.service.queue;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class EstimativaTempoEnvioServiceTest {

    @Test
    void formatarTempo_segundos() {
        assertEquals("em alguns segundos", EstimativaTempoEnvioService.formatarTempo(3));
        assertEquals("cerca de 45 segundos", EstimativaTempoEnvioService.formatarTempo(45));
    }

    @Test
    void formatarTempo_minutos() {
        assertEquals("cerca de 2 minutos", EstimativaTempoEnvioService.formatarTempo(120));
        assertEquals("cerca de 1 minuto", EstimativaTempoEnvioService.formatarTempo(60));
    }

    @Test
    void formatarTempo_horas() {
        assertEquals("cerca de 2 horas", EstimativaTempoEnvioService.formatarTempo(7200));
    }
}
