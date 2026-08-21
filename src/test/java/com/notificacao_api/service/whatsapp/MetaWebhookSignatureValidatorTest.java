package com.notificacao_api.service.whatsapp;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.notificacao_api.config.MetaWebhookProperties;

class MetaWebhookSignatureValidatorTest {

    private MetaWebhookSignatureValidator validator;

    @BeforeEach
    void setUp() {
        validator = new MetaWebhookSignatureValidator(new MetaWebhookProperties("verify", "test-app-secret"));
    }

    @Test
    void assinaturaValida() {
        byte[] body = "{\"object\":\"whatsapp_business_account\"}".getBytes();
        String signature = "sha256=" + hmac(body, "test-app-secret");
        assertTrue(validator.assinaturaValida(signature, body));
    }

    @Test
    void assinaturaInvalida() {
        byte[] body = "{\"object\":\"whatsapp_business_account\"}".getBytes();
        assertFalse(validator.assinaturaValida("sha256=deadbeef", body));
    }

    private String hmac(byte[] data, String secret) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(
                    secret.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(data);
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
}
