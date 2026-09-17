package com.notificacao_api.service.github;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class GithubWebhookSignatureValidator {

    public boolean assinaturaValida(String signatureHeader, byte[] rawBody, String webhookSecret) {
        if (!StringUtils.hasText(signatureHeader) || rawBody == null) {
            return false;
        }
        if (!StringUtils.hasText(webhookSecret)) {
            return false;
        }

        String expectedPrefix = "sha256=";
        if (!signatureHeader.startsWith(expectedPrefix)) {
            return false;
        }

        String receivedHash = signatureHeader.substring(expectedPrefix.length());
        String calculated = hmacSha256(rawBody, webhookSecret.trim());
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
            throw new IllegalStateException("Falha ao validar assinatura GitHub webhook.", ex);
        }
    }
}
