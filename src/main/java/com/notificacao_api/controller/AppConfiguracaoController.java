package com.notificacao_api.controller;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.notificacao_api.dto.configuracao.ApiKeyCreateRequest;
import com.notificacao_api.dto.configuracao.ApiKeyCreatedResponse;
import com.notificacao_api.dto.configuracao.ApiKeyResponse;
import com.notificacao_api.dto.configuracao.AuditoriaEventoResponse;
import com.notificacao_api.dto.configuracao.FeatureFlagResponse;
import com.notificacao_api.dto.configuracao.OrganizacaoConfiguracaoRequest;
import com.notificacao_api.dto.configuracao.OrganizacaoConfiguracaoResponse;
import com.notificacao_api.dto.configuracao.WebhookRequest;
import com.notificacao_api.dto.configuracao.WebhookResponse;
import com.notificacao_api.service.AuditoriaEventoService;
import com.notificacao_api.service.FeatureFlagService;
import com.notificacao_api.service.OrganizacaoApiKeyService;
import com.notificacao_api.service.OrganizacaoConfiguracaoService;
import com.notificacao_api.service.OrganizacaoWebhookService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/app")
@PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_USER')")
public class AppConfiguracaoController {

    private final OrganizacaoConfiguracaoService configuracaoService;
    private final FeatureFlagService featureFlagService;
    private final OrganizacaoApiKeyService apiKeyService;
    private final OrganizacaoWebhookService webhookService;
    private final AuditoriaEventoService auditoriaEventoService;

    public AppConfiguracaoController(
            OrganizacaoConfiguracaoService configuracaoService,
            FeatureFlagService featureFlagService,
            OrganizacaoApiKeyService apiKeyService,
            OrganizacaoWebhookService webhookService,
            AuditoriaEventoService auditoriaEventoService) {
        this.configuracaoService = configuracaoService;
        this.featureFlagService = featureFlagService;
        this.apiKeyService = apiKeyService;
        this.webhookService = webhookService;
        this.auditoriaEventoService = auditoriaEventoService;
    }

    @GetMapping("/configuracoes")
    public ResponseEntity<OrganizacaoConfiguracaoResponse> buscarConfiguracoes() {
        return ResponseEntity.ok(configuracaoService.buscarAtual());
    }

    @PutMapping("/configuracoes")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<OrganizacaoConfiguracaoResponse> atualizarConfiguracoes(
            @RequestBody OrganizacaoConfiguracaoRequest request) {
        return ResponseEntity.ok(configuracaoService.atualizarAtual(request));
    }

    @GetMapping("/configuracoes/features")
    public ResponseEntity<List<FeatureFlagResponse>> listarFeatures() {
        return ResponseEntity.ok(featureFlagService.listarDaOrganizacaoAtual());
    }

    @GetMapping("/configuracoes/api-keys")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<List<ApiKeyResponse>> listarApiKeys() {
        return ResponseEntity.ok(apiKeyService.listar());
    }

    @PostMapping("/configuracoes/api-keys")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<ApiKeyCreatedResponse> criarApiKey(@Valid @RequestBody ApiKeyCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(apiKeyService.criar(request));
    }

    @GetMapping("/configuracoes/api-keys/{idApiKey}/revogar")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<ApiKeyResponse> revogarApiKey(@PathVariable Long idApiKey) {
        return ResponseEntity.ok(apiKeyService.revogar(idApiKey));
    }

    @GetMapping("/configuracoes/webhooks")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<List<WebhookResponse>> listarWebhooks() {
        return ResponseEntity.ok(webhookService.listar());
    }

    @PostMapping("/configuracoes/webhooks")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<WebhookResponse> criarWebhook(@Valid @RequestBody WebhookRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(webhookService.criar(request));
    }

    @PutMapping("/configuracoes/webhooks/{idWebhook}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<WebhookResponse> atualizarWebhook(
            @PathVariable Long idWebhook,
            @Valid @RequestBody WebhookRequest request) {
        return ResponseEntity.ok(webhookService.atualizar(idWebhook, request));
    }

    @PatchMapping("/configuracoes/webhooks/{idWebhook}/ativar")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<WebhookResponse> ativarWebhook(@PathVariable Long idWebhook) {
        return ResponseEntity.ok(webhookService.alterarStatus(idWebhook, true));
    }

    @PatchMapping("/configuracoes/webhooks/{idWebhook}/inativar")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<WebhookResponse> inativarWebhook(@PathVariable Long idWebhook) {
        return ResponseEntity.ok(webhookService.alterarStatus(idWebhook, false));
    }

    @DeleteMapping("/configuracoes/webhooks/{idWebhook}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<Void> removerWebhook(@PathVariable Long idWebhook) {
        webhookService.remover(idWebhook);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/auditoria")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<Page<AuditoriaEventoResponse>> listarAuditoria(
            @PageableDefault(size = 20, sort = "dtCriacao", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(auditoriaEventoService.listarOrganizacao(pageable));
    }
}
