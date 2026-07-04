package com.notificacao_api.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.notificacao_api.security.JwtAuthentication;
import com.notificacao_api.service.TenantContextService;

@RestController
@RequestMapping("/app/integracao")
public class IntegracaoController {

    private final TenantContextService tenantContextService;

    public IntegracaoController(TenantContextService tenantContextService) {
        this.tenantContextService = tenantContextService;
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        JwtAuthentication auth = tenantContextService.atual();
        Long idOrganizacao = tenantContextService.idOrganizacaoObrigatoria();
        boolean apiKey = "API_KEY".equals(auth.getTipoGlobal());

        return Map.of(
                "conectada", true,
                "idOrganizacao", idOrganizacao,
                "autenticacao", apiKey ? "API_KEY" : "JWT");
    }
}
