package com.notificacao_api.security.crypto;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Service;

import com.notificacao_api.config.WhatsappCryptoProperties;

@Service
public class AesGcmEncryptionService implements EncryptionService {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;

    private final SecretKey secretKey;

    public AesGcmEncryptionService(WhatsappCryptoProperties properties) {
        String keyValue = properties.encryptionKey();
        if (keyValue == null || keyValue.isBlank()) {
            this.secretKey = null;
            return;
        }
        byte[] keyBytes = Base64.getDecoder().decode(keyValue);
        if (keyBytes.length != 32) {
            throw new IllegalStateException(
                    "WHATSAPP_CREDENTIAL_ENCRYPTION_KEY deve ter 32 bytes decodificados (AES-256).");
        }
        this.secretKey = new SecretKeySpec(keyBytes, "AES");
    }

    private void ensureKeyConfigured() {
        if (secretKey == null) {
            throw new IllegalStateException(
                    "WHATSAPP_CREDENTIAL_ENCRYPTION_KEY nao configurada. Defina a variavel de ambiente.");
        }
    }

    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    public String encrypt(String plaintext) {
        ensureKeyConfigured();
        if (plaintext == null || plaintext.isBlank()) {
            throw new IllegalArgumentException("Texto para criptografia nao pode ser vazio.");
        }

        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            return Base64.getEncoder().encodeToString(iv)
                    + ":"
                    + Base64.getEncoder().encodeToString(ciphertext);
        } catch (Exception ex) {
            throw new IllegalStateException("Falha ao criptografar credencial.", ex);
        }
    }

    @Override
    public String decrypt(String ciphertext) {
        ensureKeyConfigured();
        if (ciphertext == null || ciphertext.isBlank()) {
            throw new IllegalArgumentException("Texto criptografado invalido.");
        }

        int separator = ciphertext.indexOf(':');
        if (separator <= 0) {
            throw new IllegalArgumentException("Formato de credencial criptografada invalido.");
        }

        try {
            byte[] iv = Base64.getDecoder().decode(ciphertext.substring(0, separator));
            byte[] encrypted = Base64.getDecoder().decode(ciphertext.substring(separator + 1));

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] plaintext = cipher.doFinal(encrypted);

            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalStateException("Falha ao descriptografar credencial.", ex);
        }
    }
}
