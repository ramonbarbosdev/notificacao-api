package com.notificacao_api.service.github.app;

import java.io.StringReader;
import java.security.PrivateKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Jwts;

@Component
public class GithubAppJwtFactory {

    private static final int JWT_VALIDADE_MINUTOS = 9;

    public String criarJwt(GithubAppCredentials credenciais) {
        Instant agora = Instant.now();
        PrivateKey privateKey = lerPrivateKey(credenciais.privateKeyPem());
        return Jwts.builder()
                .issuer(String.valueOf(credenciais.appId()))
                .issuedAt(Date.from(agora))
                .expiration(Date.from(agora.plus(JWT_VALIDADE_MINUTOS, ChronoUnit.MINUTES)))
                .signWith(privateKey)
                .compact();
    }

    private PrivateKey lerPrivateKey(String pem) {
        try (PEMParser parser = new PEMParser(new StringReader(pem))) {
            Object objeto = parser.readObject();
            JcaPEMKeyConverter conversor = new JcaPEMKeyConverter();
            if (objeto instanceof PEMKeyPair keyPair) {
                return conversor.getPrivateKey(keyPair.getPrivateKeyInfo());
            }
            if (objeto instanceof PrivateKeyInfo keyInfo) {
                return conversor.getPrivateKey(keyInfo);
            }
            throw new IllegalArgumentException("Formato de chave privada GitHub App nao suportado.");
        } catch (Exception ex) {
            throw new IllegalArgumentException("Chave privada GitHub App invalida.", ex);
        }
    }
}
