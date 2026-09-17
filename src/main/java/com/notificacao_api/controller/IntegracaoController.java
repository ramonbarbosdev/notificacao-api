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
import com.notificacao_api.dto.integracao.GithubWebhookIntegracaoResponse;
import com.notificacao_api.dto.integracao.WebhookGenericoIntegracaoResponse;
import com.notificacao_api.dto.integracao.WhatsappWebhookInboundRequest;
import com.notificacao_api.dto.integracao.WhatsappWebhookInboundResponse;
import com.notificacao_api.enums.RecursoFeature;
import com.notificacao_api.service.FeatureFlagService;
import com.notificacao_api.service.github.GithubWhatsappOptInSupport;
import com.notificacao_api.dto.whatsapp.EnviarMensagemWhatsappRequisicao;
import com.notificacao_api.dto.whatsapp.EnviarMensagemWhatsappResposta;
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
    private final FeatureFlagService featureFlagService;

    public IntegracaoController(
            TenantContextService tenantContextService,
            WhatsappSessaoService whatsappSessaoService,
            AlertaOperacionalService alertaOperacionalService,
            OrganizacaoConfiguracaoService organizacaoConfiguracaoService,
            FeatureFlagService featureFlagService) {
        this.tenantContextService = tenantContextService;
        this.whatsappSessaoService = whatsappSessaoService;
        this.alertaOperacionalService = alertaOperacionalService;
        this.organizacaoConfiguracaoService = organizacaoConfiguracaoService;
        this.featureFlagService = featureFlagService;
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

    @PostMapping("/whatsapp/enviar-mensagem")
    public EnviarMensagemWhatsappResposta whatsappEnviarMensagem(
            @Valid @RequestBody EnviarMensagemWhatsappRequisicao requisicao) {
        return whatsappSessaoService.enviarMensagem(requisicao);
    }

    @GetMapping("/webhook/generico")
    public ResponseEntity<WebhookGenericoIntegracaoResponse> instrucoesWebhookGenerico() {
        Long idOrganizacao = tenantContextService.idOrganizacaoObrigatoria();
        boolean featureHabilitada = featureFlagService.estaHabilitado(idOrganizacao, RecursoFeature.WEBHOOK_GENERICO);

        String exemploJson = """
                {
                  "destinatario": "5571999999999",
                  "assunto": "Alerta sistema X",
                  "mensagem": "Texto da notificacao",
                  "referenciaExterna": "pedido-123"
                }
                """;

        return ResponseEntity.ok(new WebhookGenericoIntegracaoResponse(
                featureHabilitada,
                "/api/webhooks/generico?key={suaApiKeyCompleta}",
                "API Key com scope NOTIFICACOES_ENVIAR (header X-API-KEY ou query key=). "
                        + "Opcional: X-Webhook-Signature-256 (HMAC-SHA256 do body, prefixo sha256=, secret = API Key).",
                exemploJson.trim(),
                "Sem destinatario: padrao registra na fila bloqueado; desative com webhookRegistrarFilaSemDestinatario em PUT /app/configuracoes. "
                        + "Corpo text/plain usa o texto inteiro como mensagem."));
    }

    @GetMapping("/github/webhook")
    public ResponseEntity<GithubWebhookIntegracaoResponse> instrucoesGithubWebhook() {
        Long idOrganizacao = tenantContextService.idOrganizacaoObrigatoria();
        boolean featureHabilitada = featureFlagService.estaHabilitado(idOrganizacao, RecursoFeature.GITHUB_WEBHOOK);

        String fraseAtivacao = organizacaoConfiguracaoService.fraseAtivacaoGithubWhatsapp(idOrganizacao);
        StatusWhatsappResposta whatsapp = whatsappSessaoService.obterStatus();
        boolean conectado = Boolean.TRUE.equals(whatsapp.conectado());
        String linkAtivacao = conectado
                ? GithubWhatsappOptInSupport.montarLinkWaMe(whatsapp.telefone(), fraseAtivacao)
                : null;

        return ResponseEntity.ok(new GithubWebhookIntegracaoResponse(
                featureHabilitada,
                "/api/webhooks/github?key={suaApiKeyCompleta}",
                "No GitHub App, defina o Webhook secret com o mesmo valor da API Key completa (scope NOTIFICACOES_ENVIAR).",
                "Compartilhe o link WhatsApp com o time. Apos a frase de ativacao, o usuario informa o login GitHub "
                        + "e passa a receber alertas como assignee. Admin altera a frase em PUT /app/configuracoes "
                        + "(dsGithubFraseAtivacaoWhatsapp; vazio restaura o padrao).",
                fraseAtivacao,
                linkAtivacao,
                conectado));
    }

    @GetMapping("/whatsapp/webhook-inbound")
    public ResponseEntity<WhatsappWebhookInboundResponse> buscarWebhookInbound() {
        return ResponseEntity.ok(organizacaoConfiguracaoService.buscarWebhookInbound());
    }

    @PutMapping("/whatsapp/webhook-inbound")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','GLOBAL_API_KEY')")
    public ResponseEntity<WhatsappWebhookInboundResponse> atualizarWebhookInbound(
            @Valid @RequestBody WhatsappWebhookInboundRequest request) {
        return ResponseEntity.ok(organizacaoConfiguracaoService.atualizarWebhookInbound(request));
    }
}
