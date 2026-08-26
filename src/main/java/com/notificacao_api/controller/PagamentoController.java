package com.notificacao_api.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.notificacao_api.dto.pagamento.CobrancaResponse;
import com.notificacao_api.dto.pagamento.MetodoPagamentoResponse;
import com.notificacao_api.dto.pagamento.VincularCartaoRequest;
import com.notificacao_api.service.PagamentoService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/app/pagamentos")
public class PagamentoController {

    private final PagamentoService pagamentoService;

    public PagamentoController(PagamentoService pagamentoService) {
        this.pagamentoService = pagamentoService;
    }

    @GetMapping("/metodos")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<List<MetodoPagamentoResponse>> listarMetodos() {
        return ResponseEntity.ok(pagamentoService.listarMetodos());
    }

    @PostMapping("/cartoes")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<MetodoPagamentoResponse> vincularCartao(@Valid @RequestBody VincularCartaoRequest request) {
        return ResponseEntity.ok(pagamentoService.vincularCartao(request));
    }

    @DeleteMapping("/cartoes/{idMetodoPagamento}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<Void> removerCartao(@PathVariable Long idMetodoPagamento) {
        pagamentoService.removerCartao(idMetodoPagamento);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/cartoes/{idMetodoPagamento}/padrao")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<MetodoPagamentoResponse> definirCartaoPadrao(@PathVariable Long idMetodoPagamento) {
        return ResponseEntity.ok(pagamentoService.definirCartaoPadrao(idMetodoPagamento));
    }

    @GetMapping("/cobrancas")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<List<CobrancaResponse>> listarCobrancas() {
        return ResponseEntity.ok(pagamentoService.listarCobrancas());
    }

    @GetMapping("/cobrancas/pendentes")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<List<CobrancaResponse>> listarCobrancasPendentes() {
        return ResponseEntity.ok(pagamentoService.listarCobrancasPendentes());
    }
}
