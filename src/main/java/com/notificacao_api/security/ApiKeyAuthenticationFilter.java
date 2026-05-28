package com.notificacao_api.security;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.notificacao_api.model.OrganizacaoApiKey;
import com.notificacao_api.repository.OrganizacaoApiKeyRepository;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private static final String HEADER_API_KEY = "X-API-KEY";

    private final OrganizacaoApiKeyRepository repository;
    private final PasswordEncoder passwordEncoder;

    public ApiKeyAuthenticationFilter(
            OrganizacaoApiKeyRepository repository,
            PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String apiKey = request.getHeader(HEADER_API_KEY);
        if (apiKey == null || apiKey.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            OrganizacaoApiKey chave = autenticar(apiKey);
            List<SimpleGrantedAuthority> authorities = montarAuthorities(chave);

            SecurityContextHolder.getContext().setAuthentication(new JwtAuthentication(
                    null,
                    "API_KEY",
                    chave.getIdOrganizacao(),
                    "API_KEY",
                    authorities));

            chave.setUltimoUsoEm(LocalDateTime.now());
            repository.save(chave);
        } catch (RuntimeException ex) {
            SecurityContextHolder.clearContext();
            response.sendError(HttpStatus.UNAUTHORIZED.value(), "API Key invalida");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private OrganizacaoApiKey autenticar(String apiKey) {
        String prefixo = extrairPrefixo(apiKey);
        OrganizacaoApiKey chave = repository.findByPrefixoAndAtivoTrue(prefixo)
                .orElseThrow(() -> new IllegalArgumentException("API Key nao encontrada"));

        if (chave.getExpiraEm() != null && chave.getExpiraEm().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("API Key expirada");
        }

        if (!passwordEncoder.matches(apiKey, chave.getHashChave())) {
            throw new IllegalArgumentException("API Key invalida");
        }

        return chave;
    }

    private String extrairPrefixo(String apiKey) {
        int separador = apiKey.indexOf('.');
        if (separador <= 0) {
            throw new IllegalArgumentException("API Key sem prefixo");
        }
        return apiKey.substring(0, separador);
    }

    private List<SimpleGrantedAuthority> montarAuthorities(OrganizacaoApiKey chave) {
        List<SimpleGrantedAuthority> authorities = new ArrayList<>(Arrays.stream(chave.getScopes().split(","))
                .filter(scope -> scope != null && !scope.isBlank())
                .map(scope -> new SimpleGrantedAuthority("SCOPE_" + scope.trim()))
                .toList());

        authorities.add(new SimpleGrantedAuthority("TENANT_ACCESS"));
        authorities.add(new SimpleGrantedAuthority("GLOBAL_API_KEY"));
        return authorities;
    }
}
