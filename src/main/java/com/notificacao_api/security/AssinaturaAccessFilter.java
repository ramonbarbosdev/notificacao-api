package com.notificacao_api.security;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.notificacao_api.service.AssinaturaGateService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class AssinaturaAccessFilter extends OncePerRequestFilter {

    private final AssinaturaGateService assinaturaGateService;

    public AssinaturaAccessFilter(AssinaturaGateService assinaturaGateService) {
        this.assinaturaGateService = assinaturaGateService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        if (deveValidarAssinatura(request)) {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication instanceof JwtAuthentication jwt && jwt.getIdOrganizacao() != null) {
                if (!assinaturaGateService.podeUtilizarPlataforma(jwt.getIdOrganizacao())) {
                    response.setStatus(HttpStatus.PAYMENT_REQUIRED.value());
                    response.setContentType("application/json");
                    response.getWriter().write(
                            "{\"message\":\"Assinatura inativa ou pagamento pendente. Acesse /app/assinatura ou /app/pagamentos.\"}");
                    return;
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    private boolean deveValidarAssinatura(HttpServletRequest request) {
        String path = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }

        if (!path.startsWith("/app/")) {
            return false;
        }
        if (path.startsWith("/app/assinatura")
                || path.startsWith("/app/pagamentos")
                || path.startsWith("/app/planos/disponiveis")) {
            return false;
        }
        return true;
    }
}
