package com.notificacao_api.security;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.notificacao_api.model.UsuarioOrganizacao;
import com.notificacao_api.repository.UsuarioOrganizacaoRepository;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UsuarioOrganizacaoRepository usuarioOrganizacaoRepository;

    public JwtAuthenticationFilter(
            JwtService jwtService,
            UsuarioOrganizacaoRepository usuarioOrganizacaoRepository) {
        this.jwtService = jwtService;
        this.usuarioOrganizacaoRepository = usuarioOrganizacaoRepository;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            Claims claims = jwtService.parse(header.substring(7));
            Long idUsuario = Long.valueOf(claims.getSubject());
            String tipoGlobal = claims.get("tipoGlobal", String.class);
            Long idOrganizacao = extrairLong(claims, "idOrganizacao");
            String role = claims.get("role", String.class);

            List<SimpleGrantedAuthority> authorities = new ArrayList<>();
            authorities.add(new SimpleGrantedAuthority("GLOBAL_" + tipoGlobal));

            if (idOrganizacao != null) {
                UsuarioOrganizacao vinculo = usuarioOrganizacaoRepository
                        .findByUsuarioIdUsuarioAndOrganizacaoIdOrganizacaoAndFlAtivoTrueAndOrganizacaoFlAtivoTrue(
                                idUsuario,
                                idOrganizacao)
                        .orElse(null);

                if (vinculo == null) {
                    SecurityContextHolder.clearContext();
                    filterChain.doFilter(request, response);
                    return;
                }

                role = vinculo.getDsRole();
                authorities.add(new SimpleGrantedAuthority("TENANT_ACCESS"));
                authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
            }

            SecurityContextHolder.getContext().setAuthentication(new JwtAuthentication(
                    idUsuario,
                    tipoGlobal,
                    idOrganizacao,
                    role,
                    authorities));
        } catch (RuntimeException ex) {
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }

    private Long extrairLong(Claims claims, String chave) {
        Object valor = claims.get(chave);
        if (valor instanceof Number number) {
            return number.longValue();
        }
        if (valor instanceof String texto && !texto.isBlank()) {
            return Long.valueOf(texto);
        }
        return null;
    }
}
