package com.notificacao_api.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.notificacao_api.dto.integracao.github.GithubIntegracaoCompartilhadoPatchRequest;
import com.notificacao_api.dto.integracao.github.GithubIntegracaoCompartilhadoResponse;
import com.notificacao_api.dto.integracao.github.GithubIntegracaoHubResponse;
import com.notificacao_api.dto.integracao.github.GithubIntegracaoIssueCommentModuloResponse;
import com.notificacao_api.dto.integracao.github.GithubIntegracaoProjectsV2PatchRequest;
import com.notificacao_api.dto.integracao.github.GithubIntegracaoProjectsV2Response;
import com.notificacao_api.service.TenantContextService;
import com.notificacao_api.service.github.GithubIntegracaoAppService;

@RestController
@RequestMapping("/app/integracao/github")
@PreAuthorize("hasAnyAuthority('ROLE_ADMIN','GLOBAL_API_KEY')")
public class GithubIntegracaoController {

    private final TenantContextService tenantContextService;
    private final GithubIntegracaoAppService githubIntegracaoAppService;

    public GithubIntegracaoController(
            TenantContextService tenantContextService, GithubIntegracaoAppService githubIntegracaoAppService) {
        this.tenantContextService = tenantContextService;
        this.githubIntegracaoAppService = githubIntegracaoAppService;
    }

    @GetMapping
    public ResponseEntity<GithubIntegracaoHubResponse> hub() {
        Long idOrganizacao = tenantContextService.idOrganizacaoObrigatoria();
        return ResponseEntity.ok(githubIntegracaoAppService.obterHub(idOrganizacao));
    }

    @GetMapping("/compartilhado")
    public ResponseEntity<GithubIntegracaoCompartilhadoResponse> obterCompartilhado() {
        Long idOrganizacao = tenantContextService.idOrganizacaoObrigatoria();
        return ResponseEntity.ok(githubIntegracaoAppService.obterCompartilhado(idOrganizacao));
    }

    @PatchMapping("/compartilhado")
    public ResponseEntity<GithubIntegracaoCompartilhadoResponse> patchCompartilhado(
            @RequestBody GithubIntegracaoCompartilhadoPatchRequest request) {
        Long idOrganizacao = tenantContextService.idOrganizacaoObrigatoria();
        return ResponseEntity.ok(githubIntegracaoAppService.patchCompartilhado(idOrganizacao, request));
    }

    @GetMapping("/modulos/projects-v2")
    public ResponseEntity<GithubIntegracaoProjectsV2Response> obterProjectsV2() {
        Long idOrganizacao = tenantContextService.idOrganizacaoObrigatoria();
        return ResponseEntity.ok(githubIntegracaoAppService.obterProjectsV2(idOrganizacao));
    }

    @PatchMapping("/modulos/projects-v2")
    public ResponseEntity<GithubIntegracaoProjectsV2Response> patchProjectsV2(
            @RequestBody GithubIntegracaoProjectsV2PatchRequest request) {
        Long idOrganizacao = tenantContextService.idOrganizacaoObrigatoria();
        return ResponseEntity.ok(githubIntegracaoAppService.patchProjectsV2(idOrganizacao, request));
    }

    @GetMapping("/modulos/issue-comment")
    public ResponseEntity<GithubIntegracaoIssueCommentModuloResponse> obterIssueComment() {
        Long idOrganizacao = tenantContextService.idOrganizacaoObrigatoria();
        return ResponseEntity.ok(githubIntegracaoAppService.obterIssueComment(idOrganizacao));
    }
}
