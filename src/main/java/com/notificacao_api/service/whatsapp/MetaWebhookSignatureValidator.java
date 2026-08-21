package com.notificacao_api.service.whatsapp;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Service;

import com.notificacao_api.config.MetaWebhookProperties;

@Service
public class MetaWebhookSignatureValidator {

    private final MetaWebhookProperties properties;

    public MetaWebhookSignatureValidator(MetaWebhookProperties properties) {
        this.properties = properties;
    }

    public boolean assinaturaValida(String signatureHeader, byte[] rawBody) {
        if (signatureHeader == null || signatureHeader.isBlank()) {
            return false;
        }
        String appSecret = properties.appSecret();
        if (appSecret == null || appSecret.isBlank()) {
            return false;
        }

        String expectedPrefix = "sha256=";
        if (!signatureHeader.startsWith(expectedPrefix)) {
            return false;
        }

        String receivedHash = signatureHeader.substring(expectedPrefix.length());
        String calculated = hmacSha256(rawBody, appSecret);
        return receivedHash.equals(calculated);
    }

    private String hmacSha256(byte[] data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(data);
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException | InvalidKeyException ex) {
            throw new IllegalStateException("Falha ao validar assinatura Meta webhook.", ex);
        }
    }
}
