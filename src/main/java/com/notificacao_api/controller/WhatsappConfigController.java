package com.notificacao_api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.notificacao_api.dto.whatsapp.WhatsappConfigCreateRequest;
import com.notificacao_api.dto.whatsapp.WhatsappConfigResponse;
import com.notificacao_api.dto.whatsapp.WhatsappConfigTestResponse;
import com.notificacao_api.dto.whatsapp.WhatsappConfigUpdateRequest;
import com.notificacao_api.service.whatsapp.WhatsappConfigurationService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/app/whatsapp/config")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class WhatsappConfigController {

    private final WhatsappConfigurationService configurationService;

    public WhatsappConfigController(WhatsappConfigurationService configurationService) {
        this.configurationService = configurationService;
    }

    @GetMapping
    public ResponseEntity<WhatsappConfigResponse> buscar() {
        return configurationService.buscarConfiguracaoAtual()
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Configuracao WhatsApp Cloud API nao encontrada."));
    }

    @PostMapping
    public ResponseEntity<WhatsappConfigResponse> criar(@Valid @RequestBody WhatsappConfigCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(configurationService.criar(request));
    }

    @PutMapping
    public ResponseEntity<WhatsappConfigResponse> atualizar(@Valid @RequestBody WhatsappConfigUpdateRequest request) {
        return ResponseEntity.ok(configurationService.atualizar(request));
    }

    @DeleteMapping
    public ResponseEntity<Void> desativar() {
        configurationService.desativar();
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/test")
    public ResponseEntity<WhatsappConfigTestResponse> testar() {
        return ResponseEntity.ok(configurationService.testarConexao());
    }
}
