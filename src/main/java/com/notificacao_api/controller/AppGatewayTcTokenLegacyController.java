package com.notificacao_api.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Respostas vazias para clientes antigos da tela "Saúde Gateway" (/app/gateway-saude),
 * removida em favor de {@code GET /app/whatsapp/tctoken-audiencia}.
 */
@RestController
@RequestMapping("/app/gateway/tctoken")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class AppGatewayTcTokenLegacyController {

    private static final String AVISO =
            "Endpoint descontinuado. Use Monitoracao WhatsApp: GET /app/whatsapp/tctoken-audiencia.";

    @GetMapping("/eventos")
    public ResponseEntity<Map<String, Object>> eventos(
            @RequestParam(name = "limit", defaultValue = "50") int limit,
            @RequestParam(name = "telefone", required = false) String telefone) {
        return respostaVazia("eventos", limit, telefone);
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> stats() {
        java.util.Map<String, Object> kpis = new java.util.LinkedHashMap<>();
        kpis.put("validos", 0);
        kpis.put("expirando7d", 0);
        kpis.put("semToken", 0);
        kpis.put("erros463_24h", 0);
        kpis.put("taxaEntrega24h", null);
        return ResponseEntity.ok(Map.of(
                "sucesso", true,
                "aviso", AVISO,
                "kpis", kpis));
    }

    @GetMapping("/destinatarios")
    public ResponseEntity<Map<String, Object>> destinatarios(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        return ResponseEntity.ok(Map.of(
                "sucesso", true,
                "aviso", AVISO,
                "destinatarios", List.of(),
                "total", 0,
                "page", page,
                "size", size,
                "totalPages", 0));
    }

    private static ResponseEntity<Map<String, Object>> respostaVazia(
            String chaveLista, int limit, String telefone) {
        return ResponseEntity.ok(Map.of(
                "sucesso", true,
                "aviso", AVISO,
                chaveLista, List.of(),
                "total", 0,
                "limit", limit,
                "telefone", telefone != null ? telefone : ""));
    }
}
