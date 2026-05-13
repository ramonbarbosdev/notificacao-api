package com.notificacao_api.security;

import java.util.Collection;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

public class JwtAuthentication extends AbstractAuthenticationToken {

    private final Long idUsuario;
    private final String tipoGlobal;
    private final Long idOrganizacao;
    private final String role;

    public JwtAuthentication(
            Long idUsuario,
            String tipoGlobal,
            Long idOrganizacao,
            String role,
            Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.idUsuario = idUsuario;
        this.tipoGlobal = tipoGlobal;
        this.idOrganizacao = idOrganizacao;
        this.role = role;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return "";
    }

    @Override
    public Object getPrincipal() {
        return idUsuario;
    }

    public Long getIdUsuario() {
        return idUsuario;
    }

    public String getTipoGlobal() {
        return tipoGlobal;
    }

    public Long getIdOrganizacao() {
        return idOrganizacao;
    }

    public String getRole() {
        return role;
    }
}
