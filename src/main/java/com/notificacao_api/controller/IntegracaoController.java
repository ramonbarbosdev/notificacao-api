package com.notificacao_api.controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.notificacao_api.dto.alerta.AlertaOperacionalRegistrarRequest;
import com.notificacao_api.dto.alerta.AlertaOperacionalResponse;
import com.notificacao_api.dto.integracao.EmailAlertasIntegracaoRequest;
import com.notificacao_api.dto.integracao.GithubGraphqlConsultaRequest;
import com.notificacao_api.dto.integracao.GithubGraphqlConsultaResponse;
import com.notificacao_api.dto.integracao.GithubWebhookDecisaoListaResponse;
import com.notificacao_api.dto.integracao.GithubProjectV2ListaResponse;
import com.notificacao_api.dto.integracao.GithubProjectV2StatusOpcoesResponse;
import com.notificacao_api.dto.integracao.GithubProjectV2VinculoResponse;
import com.notificacao_api.dto.integracao.GithubResponsavelAtualizarRequest;
import com.notificacao_api.dto.integracao.GithubResponsavelResponse;
import com.notificacao_api.dto.integracao.GithubWebhookIntegracaoResponse;
import com.notificacao_api.dto.integracao.GithubWebhookTemplatePreviewRequest;
import com.notificacao_api.dto.integracao.GithubWebhookTemplatePreviewResponse;
import com.notificacao_api.dto.integracao.WebhookGenericoIntegracaoResponse;
import com.notificacao_api.dto.integracao.WhatsappWebhookInboundRequest;
import com.notificacao_api.dto.integracao.WhatsappWebhookInboundResponse;
import com.notificacao_api.enums.RecursoFeature;
import com.notificacao_api.service.FeatureFlagService;
import com.notificacao_api.service.github.GithubWebhookTemplateCatalog;
import com.notificacao_api.service.github.OrganizacaoGithubResponsavelService;
import com.notificacao_api.service.github.graphql.GithubGraphqlConsultaService;
import com.notificacao_api.service.github.graphql.GithubProjectV2IntegracaoService;
import com.notificacao_api.service.github.GithubWebhookDecisaoLogService;
import com.notificacao_api.service.github.GithubWebhookWhatsappTemplateService;
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
    private final GithubWebhookWhatsappTemplateService githubWebhookWhatsappTemplateService;
    private final OrganizacaoGithubResponsavelService githubResponsavelService;
    private final GithubGraphqlConsultaService githubGraphqlConsultaService;
    private final GithubProjectV2IntegracaoService githubProjectV2IntegracaoService;
    private final GithubWebhookDecisaoLogService githubWebhookDecisaoLogService;

    public IntegracaoController(
            TenantContextService tenantContextService,
            WhatsappSessaoService whatsappSessaoService,
            AlertaOperacionalService alertaOperacionalService,
            OrganizacaoConfiguracaoService organizacaoConfiguracaoService,
            FeatureFlagService featureFlagService,
            GithubWebhookWhatsappTemplateService githubWebhookWhatsappTemplateService,
            OrganizacaoGithubResponsavelService githubResponsavelService,
            GithubGraphqlConsultaService githubGraphqlConsultaService,
            GithubProjectV2IntegracaoService githubProjectV2IntegracaoService,
            GithubWebhookDecisaoLogService githubWebhookDecisaoLogService) {
        this.tenantContextService = tenantContextService;
        this.whatsappSessaoService = whatsappSessaoService;
        this.alertaOperacionalService = alertaOperacionalService;
        this.organizacaoConfiguracaoService = organizacaoConfiguracaoService;
        this.featureFlagService = featureFlagService;
        this.githubWebhookWhatsappTemplateService = githubWebhookWhatsappTemplateService;
        this.githubResponsavelService = githubResponsavelService;
        this.githubGraphqlConsultaService = githubGraphqlConsultaService;
        this.githubProjectV2IntegracaoService = githubProjectV2IntegracaoService;
        this.githubWebhookDecisaoLogService = githubWebhookDecisaoLogService;
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
                        + "e passa a receber alertas como assignee. Admin altera a frase em PATCH "
                        + "/app/integracao/github/compartilhado (dsGithubFraseAtivacaoWhatsapp; vazio restaura o padrao).",
                fraseAtivacao,
                linkAtivacao,
                conectado,
                GithubWebhookWhatsappTemplateService.ASSUNTO_PADRAO,
                GithubWebhookWhatsappTemplateService.MENSAGEM_PADRAO,
                GithubWebhookWhatsappTemplateService.VARIAVEIS_DISPONIVEIS,
                GithubWebhookTemplateCatalog.VARIAVEIS,
                GithubWebhookTemplateCatalog.CENARIOS_PREVIEW));
    }

    @GetMapping("/github/webhook/decisoes")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','GLOBAL_API_KEY')")
    public ResponseEntity<GithubWebhookDecisaoListaResponse> listarGithubWebhookDecisoes(
            @RequestParam(name = "pagina", defaultValue = "0") int pagina,
            @RequestParam(name = "tamanho", defaultValue = "20") int tamanho) {
        return ResponseEntity.ok(githubWebhookDecisaoLogService.listarOrganizacaoAtual(pagina, tamanho));
    }

    @GetMapping("/github/responsaveis")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','GLOBAL_API_KEY')")
    public ResponseEntity<List<GithubResponsavelResponse>> listarGithubResponsaveis() {
        Long idOrganizacao = tenantContextService.idOrganizacaoObrigatoria();
        return ResponseEntity.ok(githubResponsavelService.listarPorOrganizacao(idOrganizacao));
    }

    @PatchMapping("/github/responsaveis/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','GLOBAL_API_KEY')")
    public ResponseEntity<GithubResponsavelResponse> atualizarGithubResponsavel(
            @PathVariable("id") Long idGithubResponsavel,
            @Valid @RequestBody GithubResponsavelAtualizarRequest request) {
        Long idOrganizacao = tenantContextService.idOrganizacaoObrigatoria();
        return ResponseEntity.ok(
                githubResponsavelService.atualizarAtivo(idOrganizacao, idGithubResponsavel, request.ativo()));
    }

    @DeleteMapping("/github/responsaveis/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','GLOBAL_API_KEY')")
    public ResponseEntity<Void> excluirGithubResponsavel(@PathVariable("id") Long idGithubResponsavel) {
        Long idOrganizacao = tenantContextService.idOrganizacaoObrigatoria();
        githubResponsavelService.excluir(idOrganizacao, idGithubResponsavel);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/github/graphql/consulta")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','GLOBAL_API_KEY')")
    public ResponseEntity<GithubGraphqlConsultaResponse> consultarGithubGraphql(
            @Valid @RequestBody GithubGraphqlConsultaRequest request) {
        return ResponseEntity.ok(githubGraphqlConsultaService.consultarOrganizacaoAtual(
                request.nodeId(), request.contentType()));
    }

    @GetMapping("/github/projects")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','GLOBAL_API_KEY')")
    public ResponseEntity<GithubProjectV2ListaResponse> listarGithubProjects(
            @RequestParam(name = "orgLogin", required = false) String orgLogin) {
        return ResponseEntity.ok(githubProjectV2IntegracaoService.listarProjects(orgLogin));
    }

    @GetMapping("/github/project/status-opcoes")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','GLOBAL_API_KEY')")
    public ResponseEntity<GithubProjectV2StatusOpcoesResponse> listarGithubProjectStatusOpcoes(
            @RequestParam(name = "projectNodeId", required = false) String projectNodeId) {
        return ResponseEntity.ok(githubProjectV2IntegracaoService.listarStatusOpcoes(projectNodeId));
    }

    @GetMapping("/github/project/vinculo")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','GLOBAL_API_KEY')")
    public ResponseEntity<GithubProjectV2VinculoResponse> obterGithubProjectVinculo() {
        return ResponseEntity.ok(githubProjectV2IntegracaoService.obterVinculo());
    }

    @PostMapping("/github/webhook/template/preview")
    public ResponseEntity<GithubWebhookTemplatePreviewResponse> previewTemplateGithub(
            @Valid @RequestBody GithubWebhookTemplatePreviewRequest request) {
        return ResponseEntity.ok(githubWebhookWhatsappTemplateService.preview(
                request.templateAssunto(),
                request.templateMensagem(),
                request.cenarioId()));
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
