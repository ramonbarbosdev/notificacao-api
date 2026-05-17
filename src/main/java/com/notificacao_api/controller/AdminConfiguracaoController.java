package com.notificacao_api.controller;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.notificacao_api.dto.configuracao.AuditoriaEventoResponse;
import com.notificacao_api.dto.configuracao.ConfiguracaoGlobalRequest;
import com.notificacao_api.dto.configuracao.ConfiguracaoGlobalResponse;
import com.notificacao_api.dto.configuracao.FeatureFlagRequest;
import com.notificacao_api.dto.configuracao.FeatureFlagResponse;
import com.notificacao_api.dto.configuracao.PlanoRequest;
import com.notificacao_api.dto.configuracao.PlanoResponse;
import com.notificacao_api.service.AuditoriaEventoService;
import com.notificacao_api.service.ConfiguracaoGlobalService;
import com.notificacao_api.service.FeatureFlagService;
import com.notificacao_api.service.PlanoService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/admin")
@PreAuthorize("hasAuthority('GLOBAL_SUPER_ADMIN')")
public class AdminConfiguracaoController {

    private final ConfiguracaoGlobalService configuracaoGlobalService;
    private final PlanoService planoService;
    private final FeatureFlagService featureFlagService;
    private final AuditoriaEventoService auditoriaEventoService;

    public AdminConfiguracaoController(
            ConfiguracaoGlobalService configuracaoGlobalService,
            PlanoService planoService,
            FeatureFlagService featureFlagService,
            AuditoriaEventoService auditoriaEventoService) {
        this.configuracaoGlobalService = configuracaoGlobalService;
        this.planoService = planoService;
        this.featureFlagService = featureFlagService;
        this.auditoriaEventoService = auditoriaEventoService;
    }

    @GetMapping("/configuracoes")
    public ResponseEntity<ConfiguracaoGlobalResponse> buscarConfiguracoes() {
        return ResponseEntity.ok(configuracaoGlobalService.buscar());
    }

    @PutMapping("/configuracoes")
    public ResponseEntity<ConfiguracaoGlobalResponse> atualizarConfiguracoes(
            @RequestBody ConfiguracaoGlobalRequest request) {
        return ResponseEntity.ok(configuracaoGlobalService.atualizar(request));
    }

    @GetMapping("/planos")
    public ResponseEntity<List<PlanoResponse>> listarPlanos() {
        return ResponseEntity.ok(planoService.listar());
    }

    @GetMapping("/planos/{idPlano}")
    public ResponseEntity<PlanoResponse> buscarPlano(@PathVariable Long idPlano) {
        return ResponseEntity.ok(planoService.buscar(idPlano));
    }

    @PostMapping("/planos")
    public ResponseEntity<PlanoResponse> criarPlano(@Valid @RequestBody PlanoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(planoService.criar(request));
    }

    @PutMapping("/planos/{idPlano}")
    public ResponseEntity<PlanoResponse> atualizarPlano(
            @PathVariable Long idPlano,
            @Valid @RequestBody PlanoRequest request) {
        return ResponseEntity.ok(planoService.atualizar(idPlano, request));
    }

    @PatchMapping("/planos/{idPlano}/ativar")
    public ResponseEntity<PlanoResponse> ativarPlano(@PathVariable Long idPlano) {
        return ResponseEntity.ok(planoService.alterarStatus(idPlano, true));
    }

    @PatchMapping("/planos/{idPlano}/inativar")
    public ResponseEntity<PlanoResponse> inativarPlano(@PathVariable Long idPlano) {
        return ResponseEntity.ok(planoService.alterarStatus(idPlano, false));
    }

    @GetMapping("/organizacoes/{idOrganizacao}/features")
    public ResponseEntity<List<FeatureFlagResponse>> listarFeatures(@PathVariable Long idOrganizacao) {
        return ResponseEntity.ok(featureFlagService.listarAdmin(idOrganizacao));
    }

    @PutMapping("/organizacoes/{idOrganizacao}/features")
    public ResponseEntity<List<FeatureFlagResponse>> atualizarFeatures(
            @PathVariable Long idOrganizacao,
            @RequestBody FeatureFlagRequest request) {
        return ResponseEntity.ok(featureFlagService.atualizar(idOrganizacao, request));
    }

    @GetMapping("/auditoria")
    public ResponseEntity<Page<AuditoriaEventoResponse>> listarAuditoria(
            @PageableDefault(size = 20, sort = "dtCriacao", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(auditoriaEventoService.listarGlobal(pageable));
    }
}
