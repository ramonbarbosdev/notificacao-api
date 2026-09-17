package com.notificacao_api.service;

import java.time.LocalDateTime;
import java.util.Arrays;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.notificacao_api.enums.ApiKeyScope;
import com.notificacao_api.model.OrganizacaoApiKey;
import com.notificacao_api.repository.OrganizacaoApiKeyRepository;

@Service
public class OrganizacaoApiKeyAuthenticationService {

    private final OrganizacaoApiKeyRepository repository;
    private final PasswordEncoder passwordEncoder;

    public OrganizacaoApiKeyAuthenticationService(
            OrganizacaoApiKeyRepository repository,
            PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
    }

    public OrganizacaoApiKey autenticar(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "API Key obrigatoria.");
        }

        String prefixo = extrairPrefixo(apiKey.trim());
        OrganizacaoApiKey chave = repository.findByPrefixoAndAtivoTrue(prefixo)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "API Key invalida."));

        if (chave.getExpiraEm() != null && chave.getExpiraEm().isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "API Key expirada.");
        }

        if (!passwordEncoder.matches(apiKey.trim(), chave.getHashChave())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "API Key invalida.");
        }

        chave.setUltimoUsoEm(LocalDateTime.now());
        repository.save(chave);
        return chave;
    }

    public void validarScope(OrganizacaoApiKey chave, ApiKeyScope scopeObrigatorio) {
        boolean possui = Arrays.stream(chave.getScopes().split(","))
                .map(String::trim)
                .anyMatch(scope -> scope.equals(scopeObrigatorio.name()));
        if (!possui) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "API Key sem permissao " + scopeObrigatorio.name() + ".");
        }
    }

    private String extrairPrefixo(String apiKey) {
        int separador = apiKey.indexOf('.');
        if (separador <= 0) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "API Key sem prefixo.");
        }
        return apiKey.substring(0, separador);
    }
}
