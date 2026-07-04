package com.notificacao_api.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.notificacao_api.dto.whatsapp.StatusWhatsappResposta;
import com.notificacao_api.security.JwtAuthentication;
import com.notificacao_api.service.TenantContextService;
import com.notificacao_api.service.whatsapp.WhatsappSessaoService;

@RestController
@RequestMapping("/app/integracao")
public class IntegracaoController {

    private final TenantContextService tenantContextService;
    private final WhatsappSessaoService whatsappSessaoService;

    public IntegracaoController(
            TenantContextService tenantContextService,
            WhatsappSessaoService whatsappSessaoService) {
        this.tenantContextService = tenantContextService;
        this.whatsappSessaoService = whatsappSessaoService;
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
}
