package com.notificacao_api.security.crypto;

public interface EncryptionService {

    String encrypt(String plaintext);

    String decrypt(String ciphertext);
}
