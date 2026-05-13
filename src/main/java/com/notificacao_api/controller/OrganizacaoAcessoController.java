package com.notificacao_api.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.notificacao_api.security.JwtAuthentication;
import com.notificacao_api.service.TenantContextService;

@RestController
@RequestMapping("/app/organizacao")
public class OrganizacaoAcessoController {

    private final TenantContextService tenantContextService;

    public OrganizacaoAcessoController(TenantContextService tenantContextService) {
        this.tenantContextService = tenantContextService;
    }

    @GetMapping("/acesso")
    public ResponseEntity<Map<String, Object>> acessoAdminOuUsuarioDaOrganizacao() {
        JwtAuthentication atual = tenantContextService.atual();
        tenantContextService.idOrganizacaoObrigatoria();

        return ResponseEntity.ok(Map.of(
                "message", "Acesso permitido para ADMIN ou USER da organizacao",
                "idUsuario", atual.getIdUsuario(),
                "idOrganizacao", atual.getIdOrganizacao(),
                "role", atual.getRole()));
    }
}
