package com.notificacao_api.security;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Service
public class JwtService {

    private final SecretKey secretKey;
    private final long expirationMinutes;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration-minutes}") long expirationMinutes) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMinutes = expirationMinutes;
    }

    public String gerarTokenSemTenant(Long idUsuario, String tipoGlobal) {
        return baseBuilder(idUsuario)
                .claim("tipoGlobal", tipoGlobal)
                .compact();
    }

    public String gerarTokenComTenant(Long idUsuario, Long idOrganizacao, String role) {
        return baseBuilder(idUsuario)
                .claim("tipoGlobal", "DEFAULT")
                .claim("idOrganizacao", idOrganizacao)
                .claim("role", role)
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private io.jsonwebtoken.JwtBuilder baseBuilder(Long idUsuario) {
        Instant agora = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(idUsuario))
                .issuedAt(Date.from(agora))
                .expiration(Date.from(agora.plus(expirationMinutes, ChronoUnit.MINUTES)))
                .signWith(secretKey);
    }
}
