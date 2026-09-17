package com.notificacao_api.shared;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;

import org.junit.jupiter.api.Test;

class TextoTemplateUtilTest {

    @Test
    void substituiComChavesUnicode() {
        String template = "\uFF5B\uFF5Btitulo\uFF5D\uFF5D";
        String resultado = TextoTemplateUtil.substituir(template, Map.of("titulo", "Issue 1"));
        assertEquals("Issue 1", resultado);
    }
}
