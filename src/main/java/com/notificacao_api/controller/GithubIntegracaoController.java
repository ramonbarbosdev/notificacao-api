package com.notificacao_api.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.notificacao_api.dto.integracao.github.GithubIntegracaoCompartilhadoPatchRequest;
import com.notificacao_api.dto.integracao.github.GithubIntegracaoCompartilhadoResponse;
import com.notificacao_api.dto.integracao.github.GithubIntegracaoHubResponse;
import com.notificacao_api.dto.integracao.github.GithubIntegracaoIssueCommentModuloResponse;
import com.notificacao_api.dto.integracao.github.GithubIntegracaoModuloHabilitadoPatchRequest;
import com.notificacao_api.dto.integracao.github.GithubIntegracaoModuloStatusResponse;
import com.notificacao_api.dto.integracao.github.GithubIntegracaoProjectsV2PatchRequest;
import com.notificacao_api.dto.integracao.github.GithubIntegracaoProjectsV2Response;
import com.notificacao_api.service.TenantContextService;
import com.notificacao_api.service.github.GithubIntegracaoAppService;

@RestController
@RequestMapping("/app/integracao/github")
public class GithubIntegracaoController {

    private static final String LEITURA_GITHUB =
            "hasAnyAuthority('ROLE_ADMIN','ROLE_USER','SCOPE_NOTIFICACOES_ENVIAR')";
    private static final String ESCRITA_ADMIN = "hasAuthority('ROLE_ADMIN')";

    private final TenantContextService tenantContextService;
    private final GithubIntegracaoAppService githubIntegracaoAppService;

    public GithubIntegracaoController(
            TenantContextService tenantContextService, GithubIntegracaoAppService githubIntegracaoAppService) {
        this.tenantContextService = tenantContextService;
        this.githubIntegracaoAppService = githubIntegracaoAppService;
    }

    @GetMapping
    @PreAuthorize(LEITURA_GITHUB)
    public ResponseEntity<GithubIntegracaoHubResponse> hub() {
        Long idOrganizacao = tenantContextService.idOrganizacaoObrigatoria();
        return ResponseEntity.ok(githubIntegracaoAppService.obterHub(idOrganizacao));
    }

    @GetMapping("/compartilhado")
    @PreAuthorize(LEITURA_GITHUB)
    public ResponseEntity<GithubIntegracaoCompartilhadoResponse> obterCompartilhado() {
        Long idOrganizacao = tenantContextService.idOrganizacaoObrigatoria();
        return ResponseEntity.ok(githubIntegracaoAppService.obterCompartilhado(idOrganizacao));
    }

    @PatchMapping("/compartilhado")
    @PreAuthorize(ESCRITA_ADMIN)
    public ResponseEntity<GithubIntegracaoCompartilhadoResponse> patchCompartilhado(
            @RequestBody GithubIntegracaoCompartilhadoPatchRequest request) {
        Long idOrganizacao = tenantContextService.idOrganizacaoObrigatoria();
        return ResponseEntity.ok(githubIntegracaoAppService.patchCompartilhado(idOrganizacao, request));
    }

    @GetMapping("/modulos/projects-v2")
    @PreAuthorize(LEITURA_GITHUB)
    public ResponseEntity<GithubIntegracaoProjectsV2Response> obterProjectsV2() {
        Long idOrganizacao = tenantContextService.idOrganizacaoObrigatoria();
        return ResponseEntity.ok(githubIntegracaoAppService.obterProjectsV2(idOrganizacao));
    }

    @PatchMapping("/modulos/projects-v2")
    @PreAuthorize(ESCRITA_ADMIN)
    public ResponseEntity<GithubIntegracaoProjectsV2Response> patchProjectsV2(
            @RequestBody GithubIntegracaoProjectsV2PatchRequest request) {
        Long idOrganizacao = tenantContextService.idOrganizacaoObrigatoria();
        return ResponseEntity.ok(githubIntegracaoAppService.patchProjectsV2(idOrganizacao, request));
    }

    @GetMapping("/modulos/issue-comment")
    @PreAuthorize(LEITURA_GITHUB)
    public ResponseEntity<GithubIntegracaoIssueCommentModuloResponse> obterIssueComment() {
        Long idOrganizacao = tenantContextService.idOrganizacaoObrigatoria();
        return ResponseEntity.ok(githubIntegracaoAppService.obterIssueComment(idOrganizacao));
    }

    @PatchMapping("/modulos/{codigoModulo}/habilitado")
    @PreAuthorize(ESCRITA_ADMIN)
    public ResponseEntity<GithubIntegracaoModuloStatusResponse> patchModuloHabilitado(
            @PathVariable String codigoModulo,
            @RequestBody GithubIntegracaoModuloHabilitadoPatchRequest request) {
        Long idOrganizacao = tenantContextService.idOrganizacaoObrigatoria();
        return ResponseEntity.ok(
                githubIntegracaoAppService.patchModuloHabilitado(idOrganizacao, codigoModulo, request.habilitado()));
    }
}
