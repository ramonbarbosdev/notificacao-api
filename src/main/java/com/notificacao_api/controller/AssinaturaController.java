package com.notificacao_api.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.notificacao_api.dto.pagamento.AssinaturaResponse;
import com.notificacao_api.dto.pagamento.ContratarAssinaturaRequest;
import com.notificacao_api.dto.pagamento.PlanoDisponivelResponse;
import com.notificacao_api.service.AssinaturaService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/app")
public class AssinaturaController {

    private final AssinaturaService assinaturaService;

    public AssinaturaController(AssinaturaService assinaturaService) {
        this.assinaturaService = assinaturaService;
    }

    @GetMapping("/planos/disponiveis")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<List<PlanoDisponivelResponse>> listarPlanosDisponiveis() {
        return ResponseEntity.ok(assinaturaService.listarPlanosDisponiveis());
    }

    @GetMapping("/assinatura")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<AssinaturaResponse> buscarAssinatura() {
        return ResponseEntity.ok(assinaturaService.buscarAtual());
    }

    @PostMapping("/assinatura")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<AssinaturaResponse> contratar(@Valid @RequestBody ContratarAssinaturaRequest request) {
        return ResponseEntity.ok(assinaturaService.contratar(request));
    }

    @DeleteMapping("/assinatura")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<AssinaturaResponse> cancelar() {
        return ResponseEntity.ok(assinaturaService.cancelar());
    }
}
