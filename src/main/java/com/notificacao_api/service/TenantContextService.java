package com.notificacao_api.service;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.notificacao_api.security.JwtAuthentication;

@Service
public class TenantContextService {

    public JwtAuthentication atual() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthentication jwtAuthentication && jwtAuthentication.isAuthenticated()) {
            return jwtAuthentication;
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario nao autenticado");
    }

    public Long idOrganizacaoObrigatoria() {
        Long idOrganizacao = atual().getIdOrganizacao();
        if (idOrganizacao == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Selecione uma organizacao");
        }
        return idOrganizacao;
    }
}
