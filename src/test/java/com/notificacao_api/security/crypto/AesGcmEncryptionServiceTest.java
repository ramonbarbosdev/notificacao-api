package com.notificacao_api.security.crypto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.notificacao_api.config.WhatsappCryptoProperties;

class AesGcmEncryptionServiceTest {

    private AesGcmEncryptionService encryptionService;

    @BeforeEach
    void setUp() {
        encryptionService = new AesGcmEncryptionService(
                new WhatsappCryptoProperties("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="));
    }

    @Test
    void roundTripEncryptDecrypt() {
        String original = "EAAG-test-token-12345";
        String encrypted = encryptionService.encrypt(original);

        assertNotEquals(original, encrypted);
        assertEquals(original, encryptionService.decrypt(encrypted));
    }

    @Test
    void falhaSemChaveConfigurada() {
        AesGcmEncryptionService semChave = new AesGcmEncryptionService(new WhatsappCryptoProperties(""));
        assertThrows(IllegalStateException.class, () -> semChave.encrypt("token"));
    }
}
