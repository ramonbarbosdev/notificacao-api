package com.notificacao_api.service.github;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GithubWebhookSignatureValidatorTest {

    private GithubWebhookSignatureValidator validator;

    @BeforeEach
    void setUp() {
        validator = new GithubWebhookSignatureValidator();
    }

    @Test
    void assinaturaValida() {
        byte[] body = "{\"action\":\"moved\"}".getBytes();
        String secret = "nak_test.api-key-secret";
        String signature = "sha256=" + hmac(body, secret);
        assertTrue(validator.assinaturaValida(signature, body, secret));
    }

    @Test
    void assinaturaInvalida() {
        byte[] body = "{\"action\":\"moved\"}".getBytes();
        assertFalse(validator.assinaturaValida("sha256=deadbeef", body, "nak_test.api-key-secret"));
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
