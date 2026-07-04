package com.notificacao_api.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.notificacao_api.dto.alerta.AlertaOperacionalRegistrarRequest;
import com.notificacao_api.dto.alerta.AlertaOperacionalResponse;
import com.notificacao_api.dto.integracao.EmailAlertasIntegracaoRequest;
import com.notificacao_api.dto.whatsapp.StatusWhatsappResposta;
import com.notificacao_api.security.JwtAuthentication;
import com.notificacao_api.service.AlertaOperacionalService;
import com.notificacao_api.service.OrganizacaoConfiguracaoService;
import com.notificacao_api.service.TenantContextService;
import com.notificacao_api.service.whatsapp.WhatsappSessaoService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/app/integracao")
public class IntegracaoController {

    private final TenantContextService tenantContextService;
    private final WhatsappSessaoService whatsappSessaoService;
    private final AlertaOperacionalService alertaOperacionalService;
    private final OrganizacaoConfiguracaoService organizacaoConfiguracaoService;

    public IntegracaoController(
            TenantContextService tenantContextService,
            WhatsappSessaoService whatsappSessaoService,
            AlertaOperacionalService alertaOperacionalService,
            OrganizacaoConfiguracaoService organizacaoConfiguracaoService) {
        this.tenantContextService = tenantContextService;
        this.whatsappSessaoService = whatsappSessaoService;
        this.alertaOperacionalService = alertaOperacionalService;
        this.organizacaoConfiguracaoService = organizacaoConfiguracaoService;
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        JwtAuthentication auth = tenantContextService.atual();
        Long idOrganizacao = tenantContextService.idOrganizacaoObrigatoria();
        boolean apiKey = "API_KEY".equals(auth.getTipoGlobal());

        Map<String, Object> resposta = new LinkedHashMap<>();
        resposta.put("conectada", true);
        resposta.put("idOrganizacao", idOrganizacao);
        resposta.put("autenticacao", apiKey ? "API_KEY" : "JWT");

        try {
            StatusWhatsappResposta whatsapp = whatsappSessaoService.obterStatus();
            resposta.put("whatsappConectado", Boolean.TRUE.equals(whatsapp.conectado()));
            resposta.put("whatsappStatus", whatsapp.status());
            resposta.put("whatsappTelefone", whatsapp.telefone());
            if (whatsapp.erro() != null && !whatsapp.erro().isBlank()) {
                resposta.put("whatsappErro", whatsapp.erro());
            }
        } catch (Exception ex) {
            resposta.put("whatsappConectado", false);
            resposta.put("whatsappStatus", "ERRO");
            resposta.put("whatsappErro", ex.getMessage());
        }

        return resposta;
    }

    @PostMapping("/alertas-operacionais")
    public ResponseEntity<AlertaOperacionalResponse> registrarAlerta(
            @Valid @RequestBody AlertaOperacionalRegistrarRequest request) {
        return ResponseEntity.ok(alertaOperacionalService.registrarIntegracaoExterna(request));
    }

    @PutMapping("/email-alertas")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','GLOBAL_API_KEY')")
    public ResponseEntity<Map<String, String>> atualizarEmailAlertas(
            @Valid @RequestBody EmailAlertasIntegracaoRequest request) {
        organizacaoConfiguracaoService.atualizarEmailAlertas(request.dsEmailAlertas());
        return ResponseEntity.ok(Map.of(
                "dsEmailAlertas",
                request.dsEmailAlertas() != null ? request.dsEmailAlertas() : ""));
    }

    @PostMapping("/whatsapp/conectar")
    public StatusWhatsappResposta whatsappConectar() {
        return whatsappSessaoService.conectar();
    }

    @GetMapping("/whatsapp/status")
    public StatusWhatsappResposta whatsappStatus() {
        return whatsappSessaoService.obterStatus();
    }

    @PostMapping("/whatsapp/desconectar")
    public StatusWhatsappResposta whatsappDesconectar() {
        return whatsappSessaoService.desconectar();
    }

    @PostMapping("/whatsapp/cancelar-conexao")
    public StatusWhatsappResposta whatsappCancelarConexao() {
        return whatsappSessaoService.desconectar();
    }

    @PostMapping("/whatsapp/reativar-operacao")
    public StatusWhatsappResposta whatsappReativarOperacao() {
        return whatsappSessaoService.reativarOperacao();
    }
}
