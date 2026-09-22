package com.notificacao_api.service.github;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.notificacao_api.service.github.GithubWebhookRegrasNotificacao.Gatilho;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.notificacao_api.dto.notificacao.EnviarNotificacaoRequisicao;
import com.notificacao_api.enums.CanalNotificacao;
import com.notificacao_api.enums.RecursoFeature;
import com.notificacao_api.model.OrganizacaoConfiguracao;
import com.notificacao_api.model.github.GithubOrganizacaoConfig;
import com.notificacao_api.model.github.OrganizacaoGithubIntegracao;
import com.notificacao_api.repository.OrganizacaoConfiguracaoRepository;
import com.notificacao_api.service.FeatureFlagService;
import com.notificacao_api.service.NotificacaoService;
import com.notificacao_api.service.OrganizacaoConfiguracaoService;
import com.notificacao_api.service.OrganizacaoGithubIntegracaoSettingsService;
import com.notificacao_api.service.github.graphql.GithubGraphqlAccessTokenResolver;
import com.notificacao_api.service.github.graphql.GithubGraphqlContentResolver;
import com.notificacao_api.service.github.graphql.GithubProjectV2ContentDetalhes;

@Service
public class GithubWebhookService {

    private static final Logger log = LoggerFactory.getLogger(GithubWebhookService.class);

    private final FeatureFlagService featureFlagService;
    private final OrganizacaoConfiguracaoRepository configuracaoRepository;
    private final OrganizacaoGithubResponsavelService githubResponsavelService;
    private final ObjectMapper objectMapper;
    private final NotificacaoService notificacaoService;
    private final OrganizacaoConfiguracaoService organizacaoConfiguracaoService;
    private final GithubWebhookWhatsappTemplateService whatsappTemplateService;
    private final OrganizacaoGithubIntegracaoSettingsService githubIntegracaoSettingsService;
    private final GithubGraphqlAccessTokenResolver githubGraphqlAccessTokenResolver;
    private final GithubGraphqlContentResolver githubGraphqlContentResolver;
    private final GithubWebhookDecisaoLogService githubWebhookDecisaoLogService;
    private final GithubRegrasPorStatusService githubRegrasPorStatusService;
    private final GithubIntegracaoConfigService githubIntegracaoConfigService;

    public GithubWebhookService(
            FeatureFlagService featureFlagService,
            OrganizacaoConfiguracaoRepository configuracaoRepository,
            OrganizacaoGithubResponsavelService githubResponsavelService,
            ObjectMapper objectMapper,
            NotificacaoService notificacaoService,
            OrganizacaoConfiguracaoService organizacaoConfiguracaoService,
            GithubWebhookWhatsappTemplateService whatsappTemplateService,
            OrganizacaoGithubIntegracaoSettingsService githubIntegracaoSettingsService,
            GithubGraphqlAccessTokenResolver githubGraphqlAccessTokenResolver,
            GithubGraphqlContentResolver githubGraphqlContentResolver,
            GithubWebhookDecisaoLogService githubWebhookDecisaoLogService,
            GithubRegrasPorStatusService githubRegrasPorStatusService,
            GithubIntegracaoConfigService githubIntegracaoConfigService) {
        this.featureFlagService = featureFlagService;
        this.configuracaoRepository = configuracaoRepository;
        this.githubResponsavelService = githubResponsavelService;
        this.objectMapper = objectMapper;
        this.notificacaoService = notificacaoService;
        this.organizacaoConfiguracaoService = organizacaoConfiguracaoService;
        this.whatsappTemplateService = whatsappTemplateService;
        this.githubIntegracaoSettingsService = githubIntegracaoSettingsService;
        this.githubGraphqlAccessTokenResolver = githubGraphqlAccessTokenResolver;
        this.githubGraphqlContentResolver = githubGraphqlContentResolver;
        this.githubWebhookDecisaoLogService = githubWebhookDecisaoLogService;
        this.githubRegrasPorStatusService = githubRegrasPorStatusService;
        this.githubIntegracaoConfigService = githubIntegracaoConfigService;
    }

    public void processar(Long idOrganizacao, String githubEvent, String deliveryId, String payloadJson) {
        featureFlagService.validarRecursoHabilitado(idOrganizacao, RecursoFeature.GITHUB_WEBHOOK);

        OrganizacaoConfiguracao orgConfig = configuracaoRepository.findByIdOrganizacao(idOrganizacao)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT, "Configuracao da organizacao nao encontrada."));
        GithubOrganizacaoConfig githubConfig = githubIntegracaoConfigService.obterConfiguracao(idOrganizacao);
        OrganizacaoGithubIntegracao integracao = githubIntegracaoConfigService.obterIntegracao(idOrganizacao);

        JsonNode root;
        try {
            root = objectMapper.readTree(payloadJson);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payload JSON invalido.");
        }

        String evento = normalizarGithubEvent(githubEvent, root);
        if (!StringUtils.hasText(evento) || !evento.equals(githubEvent)) {
            log.info(
                    "GitHub webhook evento normalizado org={} header={} usado={} delivery={}",
                    idOrganizacao,
                    githubEvent,
                    evento,
                    deliveryId);
        }

        if ("issue_comment".equalsIgnoreCase(evento)) {
            registrarDecisao(
                    idOrganizacao,
                    deliveryId,
                    evento,
                    texto(root, "action"),
                    GithubWebhookDecisaoLogService.RESULTADO_IGNORADO_EVENTO,
                    "Modulo Issue comment ainda nao implementado.",
                    null,
                    null,
                    List.of(),
                    0,
                    Map.of("modulo", "ISSUE_COMMENT", "implementado", false));
            return;
        }

        if ("ping".equalsIgnoreCase(evento)) {
            log.info("GitHub webhook ping recebido org={} delivery={}", idOrganizacao, deliveryId);
            registrarDecisao(
                    idOrganizacao,
                    deliveryId,
                    evento,
                    texto(root, "action"),
                    GithubWebhookDecisaoLogService.RESULTADO_PING,
                    "Ping do GitHub App (sem notificacao).",
                    null,
                    null,
                    List.of(),
                    0,
                    Map.of());
            return;
        }

        Long installationIdWebhook = extrairInstallationId(root);
        sincronizarInstallationId(integracao, installationIdWebhook);
        sincronizarOrganizationLogin(integracao, extrairOrganizationLogin(root));
        GithubIntegracaoSettings integracaoSettings = githubIntegracaoSettingsService.resolver(integracao);

        Optional<MensagemKanban> mensagem = extrairMensagem(
                evento, root, idOrganizacao, githubConfig, integracao, integracaoSettings, installationIdWebhook);
        if (mensagem.isEmpty()) {
            String action = texto(root, "action");
            log.info(
                    "GitHub webhook ignorado (evento/acao nao tratado) org={} event={} action={} delivery={}",
                    idOrganizacao,
                    evento,
                    action,
                    deliveryId);
            registrarDecisao(
                    idOrganizacao,
                    deliveryId,
                    evento,
                    action,
                    GithubWebhookDecisaoLogService.RESULTADO_IGNORADO_EVENTO,
                    "Evento ou acao nao tratado pela integracao.",
                    null,
                    null,
                    List.of(),
                    0,
                    Map.of("action", action != null ? action : ""));
            return;
        }

        MensagemKanban dados = mensagem.get();

        Optional<GithubRegrasPorStatusService.RegraColunaResolvida> regraColuna =
                githubRegrasPorStatusService.resolverPorStatusDestino(githubConfig, dados.statusDestino());
        boolean regrasPorColunaAtivas = githubRegrasPorStatusService.temRegrasPorColunaPersistidas(githubConfig);

        boolean prAvisarHabilitado = Boolean.TRUE.equals(githubConfig.getGithubPrAvisarAvaliadores());
        boolean issueAvisarHabilitado = Boolean.TRUE.equals(githubConfig.getGithubIssueAvisarAvaliadores());
        boolean statusPermitidoPr = regraColuna
                .map(GithubRegrasPorStatusService.RegraColunaResolvida::prAvaliadores)
                .orElseGet(() -> statusPermitido(githubConfig.getDsGithubPrStatusDisparo(), dados.statusDestino()));
        boolean statusPermitidoIssue = regraColuna
                .map(GithubRegrasPorStatusService.RegraColunaResolvida::issueAvaliadores)
                .orElseGet(() ->
                        statusPermitido(githubConfig.getDsGithubIssueStatusDisparo(), dados.statusDestino()));
        boolean avisoPrAvaliadores = prAvisarHabilitado && dados.pullRequest() && statusPermitidoPr;
        boolean avisoIssueAvaliadores = issueAvisarHabilitado
                && dados.issueProjectV2()
                && !dados.pullRequest()
                && statusPermitidoIssue;
        boolean avisoAvaliadoresConfigurados = avisoPrAvaliadores || avisoIssueAvaliadores;

        Set<Gatilho> gatilhos = GithubWebhookRegrasNotificacao.classificarGatilhos(
                githubConfig, evento, dados.acao(), root);
        if (!avisoAvaliadoresConfigurados
                && !GithubWebhookRegrasNotificacao.deveNotificarPorGatilho(githubConfig, gatilhos)) {
            log.info(
                    "GitHub webhook ignorado por regras de notificacao org={} event={} action={} gatilhos={} delivery={}",
                    idOrganizacao,
                    evento,
                    dados.acao(),
                    gatilhos,
                    deliveryId);
            Map<String, Object> detalhe = new HashMap<>();
            detalhe.put("gatilhosDetectados", gatilhos.stream().map(Enum::name).toList());
            detalhe.put(
                    "githubNotificarStatusAlterado",
                    githubConfig.getGithubNotificarStatusAlterado());
            detalhe.put(
                    "githubNotificarSomenteCampoStatus",
                    githubConfig.getGithubNotificarSomenteCampoStatus());
            detalhe.put(
                    "explicacaoUsuario",
                    GithubWebhookDecisaoUsuarioTexto.explicacaoIgnoradoGatilho(evento, dados.acao(), gatilhos));
            registrarDecisao(
                    idOrganizacao,
                    deliveryId,
                    evento,
                    dados.acao(),
                    GithubWebhookDecisaoLogService.RESULTADO_IGNORADO_GATILHO,
                    "Nenhum gatilho de notificacao habilitado para este evento.",
                    dados,
                    null,
                    List.of(),
                    0,
                    detalhe);
            return;
        }

        Map<String, Object> avisosAvaliadores = new HashMap<>();
        if (prAvisarHabilitado && dados.pullRequest() && !avisoPrAvaliadores) {
            log.info(
                    "GitHub webhook PR avaliadores nao aplicado org={} delivery={} statusDestino={} filtroPr={} "
                            + "statusPermitidoPr={} (lista PR vazia = qualquer status de PR)",
                    idOrganizacao,
                    deliveryId,
                    dados.statusDestino(),
                    githubConfig.getDsGithubPrStatusDisparo(),
                    statusPermitidoPr);
            avisosAvaliadores.put("prAvaliadoresNaoAplicado", true);
            avisosAvaliadores.put("filtroPr", githubConfig.getDsGithubPrStatusDisparo());
            avisosAvaliadores.put("statusPermitidoPr", statusPermitidoPr);
        }
        if (issueAvisarHabilitado && dados.issueProjectV2() && !dados.pullRequest() && !avisoIssueAvaliadores) {
            log.info(
                    "GitHub webhook Issue avaliadores nao aplicado org={} delivery={} statusDestino={} filtroIssue={} "
                            + "statusPermitidoIssue={} (dispara ao entrar na coluna to.name; lista vazia = qualquer status)",
                    idOrganizacao,
                    deliveryId,
                    dados.statusDestino(),
                    githubConfig.getDsGithubIssueStatusDisparo(),
                    statusPermitidoIssue);
            avisosAvaliadores.put("issueAvaliadoresNaoAplicado", true);
            avisosAvaliadores.put("filtroIssue", githubConfig.getDsGithubIssueStatusDisparo());
            avisosAvaliadores.put("statusPermitidoIssue", statusPermitidoIssue);
        }

        boolean aplicarFiltroStatusGeral =
                GithubWebhookRegrasNotificacao.deveAplicarFiltroStatusColunaGeral(githubConfig, gatilhos);
        boolean fluxoGeralPermitidoNaColuna = regraColuna
                .map(GithubRegrasPorStatusService.RegraColunaResolvida::fluxoGeral)
                .orElseGet(() -> statusPermitido(githubConfig.getDsGithubStatusDisparo(), dados.statusDestino()));
        if (regrasPorColunaAtivas && regraColuna.isEmpty() && aplicarFiltroStatusGeral) {
            fluxoGeralPermitidoNaColuna =
                    statusPermitido(githubConfig.getDsGithubStatusDisparo(), dados.statusDestino());
        }
        if (!avisoAvaliadoresConfigurados
                && aplicarFiltroStatusGeral
                && !fluxoGeralPermitidoNaColuna) {
            log.info(
                    "GitHub webhook ignorado por filtro de status org={} statusDestino={} filtroGeral={} filtroPr={} "
                            + "filtroIssue={} pullRequest={} issueProjectV2={} delivery={}",
                    idOrganizacao,
                    dados.statusDestino(),
                    githubConfig.getDsGithubStatusDisparo(),
                    githubConfig.getDsGithubPrStatusDisparo(),
                    githubConfig.getDsGithubIssueStatusDisparo(),
                    dados.pullRequest(),
                    dados.issueProjectV2(),
                    deliveryId);
            Map<String, Object> detalhe = new HashMap<>(avisosAvaliadores);
            detalhe.put("filtroGeral", githubConfig.getDsGithubStatusDisparo());
            detalhe.put("filtroPr", githubConfig.getDsGithubPrStatusDisparo());
            detalhe.put("filtroIssue", githubConfig.getDsGithubIssueStatusDisparo());
            detalhe.put("filtroStatusGeralAplicado", true);
            regraColuna.ifPresent(rc -> {
                detalhe.put("regraColunaOptionId", rc.optionId());
                detalhe.put("regraColunaFluxoGeral", rc.fluxoGeral());
                detalhe.put("regraColunaPrAvaliadores", rc.prAvaliadores());
                detalhe.put("regraColunaIssueAvaliadores", rc.issueAvaliadores());
            });
            detalhe.put("regrasPorColunaAtivas", regrasPorColunaAtivas);
            detalhe.put(
                    "gatilhosDetectados",
                    gatilhos.stream().map(GithubWebhookRegrasNotificacao.Gatilho::name).toList());
            detalhe.put(
                    "gatilhosComFiltroStatus",
                    GithubWebhookRegrasNotificacao.gatilhosComFiltroStatusColunaGeral(githubConfig)
                            .stream()
                            .map(GithubWebhookRegrasNotificacao.Gatilho::name)
                            .toList());
            detalhe.put(
                    "explicacaoUsuario",
                    GithubWebhookDecisaoUsuarioTexto.explicacaoIgnoradoStatus(
                            dados.statusDestino(),
                            githubConfig.getDsGithubStatusDisparo(),
                            avisosAvaliadores,
                            gatilhos,
                            aplicarFiltroStatusGeral));
            registrarDecisao(
                    idOrganizacao,
                    deliveryId,
                    evento,
                    dados.acao(),
                    GithubWebhookDecisaoLogService.RESULTADO_IGNORADO_STATUS,
                    "Coluna de destino fora dos filtros de status (fluxo geral).",
                    dados,
                    null,
                    List.of(),
                    0,
                    detalhe);
            return;
        }

        List<String> loginsResponsaveis;
        String fluxoDestinatarios;
        if (avisoAvaliadoresConfigurados) {
            fluxoDestinatarios = avisoPrAvaliadores ? "PR_AVALIADORES" : "ISSUE_AVALIADORES";
            loginsResponsaveis =
                    GithubWebhookRegrasNotificacao.parseLoginsLista(githubConfig.getDsGithubPrLoginsAvaliadores());
            log.info(
                    "GitHub webhook avaliadores org={} event={} delivery={} tipo={} status={} logins={}",
                    idOrganizacao,
                    evento,
                    deliveryId,
                    avisoPrAvaliadores ? "PR" : "ISSUE",
                    dados.statusDestino(),
                    loginsResponsaveis);
        } else {
            fluxoDestinatarios = "GERAL";
            GithubOrganizacaoConfig configDestinatarios = regraColuna
                    .map(rc -> githubRegrasPorStatusService.configEfetivaDestinatarios(githubConfig, rc.regra()))
                    .orElse(githubConfig);
            loginsResponsaveis = GithubWebhookRegrasNotificacao.resolverLoginsDestino(
                    configDestinatarios, evento, root, dados.githubLogins(), dados.senderLogin());
            log.info(
                    "GitHub webhook analisado org={} event={} delivery={} status={} logins={}",
                    idOrganizacao,
                    evento,
                    deliveryId,
                    dados.statusDestino(),
                    loginsResponsaveis);
        }

        List<String> telefonesDestino = resolverTelefonesDestino(idOrganizacao, loginsResponsaveis);

        if (loginsResponsaveis.isEmpty()) {
            log.warn(
                    "GitHub webhook sem logins destino org={} event={} delivery={} assigneesNoCard={} sender={} "
                            + "modo={} ignorarSemResponsavel={}",
                    idOrganizacao,
                    evento,
                    deliveryId,
                    dados.githubLogins(),
                    dados.senderLogin(),
                    githubConfig.getDsGithubDestinatariosModo(),
                    githubConfig.getGithubIgnorarSemResponsavel());
        } else if (telefonesDestino.isEmpty()) {
            log.warn(
                    "GitHub webhook logins sem opt-in WhatsApp org={} event={} delivery={} logins={}",
                    idOrganizacao,
                    evento,
                    deliveryId,
                    loginsResponsaveis);
        }

        String referencia = deliveryId != null && !deliveryId.isBlank()
                ? "github:" + deliveryId
                : null;

        String codigoGatilho = avisoPrAvaliadores
                ? "PR_AVALIADORES"
                : avisoIssueAvaliadores
                        ? "ISSUE_AVALIADORES"
                        : GithubWebhookRegrasNotificacao.codigoPrincipal(gatilhos);
        String cenarioTemplateId = avisoAvaliadoresConfigurados
                ? GithubWebhookTemplateCatalog.CENARIO_PR_AVALIADORES
                : regraColuna
                        .map(rc -> githubRegrasPorStatusService.cenarioTemplateColuna(rc.regra()))
                        .orElse(null);
        GithubRegrasPorStatusService.TextoTemplateColuna textoTemplateColuna = regraColuna
                .flatMap(rc -> githubRegrasPorStatusService.textoTemplateColuna(rc.regra()))
                .orElse(null);
        GithubWebhookWhatsappTemplateService.GithubWebhookEventoDados eventoTemplate =
                new GithubWebhookWhatsappTemplateService.GithubWebhookEventoDados(
                        dados.titulo(),
                        dados.statusDestino(),
                        dados.statusAnterior(),
                        dados.contexto(),
                        dados.acao(),
                        dados.url(),
                        dados.senderLogin(),
                        dados.githubLogins(),
                        loginsResponsaveis,
                        codigoGatilho,
                        dados.numero());

        GithubWebhookWhatsappTemplateService.MensagemWhatsapp mensagemWhatsapp = whatsappTemplateService.formatar(
                githubConfig,
                evento,
                deliveryId,
                eventoTemplate,
                cenarioTemplateId,
                textoTemplateColuna);

        EnviarNotificacaoRequisicao requisicaoBase = new EnviarNotificacaoRequisicao(
                CanalNotificacao.WHATSAPP,
                "",
                mensagemWhatsapp.assunto(),
                mensagemWhatsapp.textoWhatsapp(),
                null,
                null,
                referencia);

        if (telefonesDestino.isEmpty()) {
            if (!organizacaoConfiguracaoService.deveRegistrarFilaSemDestinatario(orgConfig)) {
                log.info(
                        "GitHub webhook ignorado sem responsavel com opt-in org={} event={} delivery={} logins={}",
                        idOrganizacao,
                        evento,
                        deliveryId,
                        loginsResponsaveis);
                Map<String, Object> detalhe = new HashMap<>(avisosAvaliadores);
                detalhe.put("modoDestinatarios", githubConfig.getDsGithubDestinatariosModo());
                registrarDecisao(
                        idOrganizacao,
                        deliveryId,
                        evento,
                        dados.acao(),
                        GithubWebhookDecisaoLogService.RESULTADO_IGNORADO_SEM_OPTIN,
                        "Destinatarios sem opt-in WhatsApp e fila sem destinatario desligada.",
                        dados,
                        fluxoDestinatarios,
                        loginsResponsaveis,
                        0,
                        detalhe);
                return;
            }
            try {
                notificacaoService.enfileirarGithubSemResponsavel(
                        idOrganizacao, requisicaoBase, loginsResponsaveis);
            } catch (ResponseStatusException ex) {
                log.warn(
                        "GitHub webhook falhou ao registrar fila sem responsavel org={} delivery={} status={} motivo={}",
                        idOrganizacao,
                        deliveryId,
                        ex.getStatusCode(),
                        ex.getReason());
                throw ex;
            }
            log.info(
                    "GitHub webhook registrado na fila sem responsavel com opt-in org={} event={} delivery={} logins={}",
                    idOrganizacao,
                    evento,
                    deliveryId,
                    loginsResponsaveis);
            Map<String, Object> detalhe = new HashMap<>(avisosAvaliadores);
            detalhe.put("modoDestinatarios", githubConfig.getDsGithubDestinatariosModo());
            registrarDecisao(
                    idOrganizacao,
                    deliveryId,
                    evento,
                    dados.acao(),
                    GithubWebhookDecisaoLogService.RESULTADO_FILA_SEM_DESTINATARIO,
                    "Registrado na fila (bloqueado) — faltou opt-in WhatsApp.",
                    dados,
                    fluxoDestinatarios,
                    loginsResponsaveis,
                    0,
                    detalhe);
            return;
        }

        int enfileirados = 0;

        for (String destinatario : telefonesDestino) {
            EnviarNotificacaoRequisicao requisicao = new EnviarNotificacaoRequisicao(
                    CanalNotificacao.WHATSAPP,
                    destinatario,
                    requisicaoBase.assunto(),
                    requisicaoBase.mensagem(),
                    null,
                    null,
                    referencia);

            try {
                notificacaoService.enviarParaOrganizacao(idOrganizacao, requisicao);
                enfileirados++;
            } catch (ResponseStatusException ex) {
                log.warn(
                        "GitHub webhook falhou ao enfileirar org={} telefone={} delivery={} status={} motivo={}",
                        idOrganizacao,
                        destinatario,
                        deliveryId,
                        ex.getStatusCode(),
                        ex.getReason());
                throw ex;
            }
        }

        log.info(
                "WhatsApp enfileirado via GitHub webhook org={} event={} delivery={} destinatarios={}",
                idOrganizacao,
                evento,
                deliveryId,
                enfileirados);
        Map<String, Object> detalhe = new HashMap<>(avisosAvaliadores);
        detalhe.put("modoDestinatarios", githubConfig.getDsGithubDestinatariosModo());
        regraColuna.ifPresent(rc -> detalhe.put("regraColunaOptionId", rc.optionId()));
        detalhe.put("gatilhos", gatilhos.stream().map(Enum::name).toList());
        detalhe.put(
                "explicacaoUsuario",
                GithubWebhookDecisaoUsuarioTexto.explicacaoEnviado(fluxoDestinatarios, enfileirados, avisosAvaliadores));
        registrarDecisao(
                idOrganizacao,
                deliveryId,
                evento,
                dados.acao(),
                GithubWebhookDecisaoLogService.RESULTADO_ENVIADO,
                enfileirados > 0
                        ? "WhatsApp enfileirado para " + enfileirados + " destinatario(s)."
                        : "Processado sem destinatarios.",
                dados,
                fluxoDestinatarios,
                loginsResponsaveis,
                enfileirados,
                detalhe);
    }

    private void registrarDecisao(
            Long idOrganizacao,
            String deliveryId,
            String githubEvent,
            String action,
            String resultado,
            String descricao,
            MensagemKanban dados,
            String fluxoDestinatarios,
            List<String> loginsDestino,
            int whatsappEnfileirados,
            Map<String, Object> detalhe) {
        try {
            githubWebhookDecisaoLogService.registrar(new GithubWebhookDecisaoLogService.RegistrarDecisaoParams(
                    idOrganizacao,
                    deliveryId,
                    githubEvent,
                    action,
                    resultado,
                    descricao,
                    dados != null ? dados.titulo() : null,
                    dados != null ? dados.statusDestino() : null,
                    dados != null ? dados.statusAnterior() : null,
                    dados != null && dados.pullRequest(),
                    dados != null && dados.issueProjectV2(),
                    fluxoDestinatarios,
                    loginsDestino,
                    whatsappEnfileirados,
                    detalhe));
        } catch (Exception ex) {
            log.warn(
                    "Falha ao persistir log visual GitHub webhook org={} delivery={} motivo={}",
                    idOrganizacao,
                    deliveryId,
                    ex.getMessage());
        }
    }

    private String normalizarGithubEvent(String githubEvent, JsonNode root) {
        if (StringUtils.hasText(githubEvent)) {
            String evento = githubEvent.trim();
            if (Set.of("ping", "issues", "project_card", "projects_v2_item").contains(evento)) {
                return evento;
            }
        }
        if (root != null && !root.isNull()) {
            if (root.has("projects_v2_item")) {
                return "projects_v2_item";
            }
            if (root.has("project_card")) {
                return "project_card";
            }
            if (root.has("issue")) {
                return "issues";
            }
        }
        return githubEvent != null ? githubEvent.trim() : "";
    }

    private void sincronizarInstallationId(OrganizacaoGithubIntegracao integracao, Long installationIdWebhook) {
        if (installationIdWebhook == null || installationIdWebhook <= 0 || integracao == null) {
            return;
        }
        if (integracao.getNuGithubInstallationId() != null) {
            return;
        }
        integracao.setNuGithubInstallationId(installationIdWebhook);
        githubIntegracaoConfigService.salvarIntegracao(integracao);
        log.info(
                "GitHub installation id persistido automaticamente org={} installationId={}",
                integracao.getIdOrganizacao(),
                installationIdWebhook);
    }

    private void sincronizarOrganizationLogin(OrganizacaoGithubIntegracao integracao, String organizationLogin) {
        if (!StringUtils.hasText(organizationLogin) || integracao == null) {
            return;
        }
        if (StringUtils.hasText(integracao.getDsOrganizationLogin())) {
            return;
        }
        integracao.setDsOrganizationLogin(organizationLogin.trim());
        githubIntegracaoConfigService.salvarIntegracao(integracao);
        log.info(
                "GitHub organization login persistido automaticamente org={} githubOrg={}",
                integracao.getIdOrganizacao(),
                organizationLogin.trim());
    }

    static String extrairOrganizationLogin(JsonNode root) {
        if (root == null || root.isNull()) {
            return null;
        }
        JsonNode organization = root.get("organization");
        if (organization == null || organization.isNull()) {
            return null;
        }
        JsonNode login = organization.get("login");
        if (login == null || login.isNull()) {
            return null;
        }
        return login.asText(null);
    }

    static Long extrairInstallationId(JsonNode root) {
        if (root == null || root.isNull()) {
            return null;
        }
        JsonNode installation = root.get("installation");
        if (installation == null || installation.isNull()) {
            return null;
        }
        JsonNode id = installation.get("id");
        if (id == null || id.isNull() || !id.isNumber()) {
            return null;
        }
        return id.asLong();
    }

    private Optional<MensagemKanban> extrairMensagem(
            String githubEvent,
            JsonNode root,
            Long idOrganizacao,
            GithubOrganizacaoConfig githubConfig,
            OrganizacaoGithubIntegracao integracao,
            GithubIntegracaoSettings integracaoSettings,
            Long installationIdWebhook) {
        if ("project_card".equals(githubEvent)) {
            return extrairProjectCard(root);
        }
        if ("projects_v2_item".equals(githubEvent)) {
            return extrairProjectsV2Item(
                    root, idOrganizacao, githubConfig, integracao, integracaoSettings, installationIdWebhook);
        }
        if ("issues".equals(githubEvent)) {
            return extrairIssue(root);
        }
        return Optional.empty();
    }

    private Optional<MensagemKanban> extrairProjectCard(JsonNode root) {
        String action = texto(root, "action");
        if (!"moved".equals(action) && !"created".equals(action)) {
            return Optional.empty();
        }

        JsonNode card = root.get("project_card");
        if (card == null || card.isNull()) {
            return Optional.empty();
        }

        String coluna = texto(card, "column_name");
        if (!StringUtils.hasText(coluna)) {
            return Optional.empty();
        }

        JsonNode issue = root.get("issue");
        String titulo = issue != null ? texto(issue, "title") : "Card do projeto";
        String url = issue != null ? texto(issue, "html_url") : null;
        List<String> logins = extrairLoginsAssignees(issue);

        return Optional.of(eventoDados(
                titulo,
                coluna,
                "Cartao movido no Project (classico)",
                action,
                url,
                root,
                logins,
                false));
    }

    private Optional<MensagemKanban> extrairProjectsV2Item(
            JsonNode root,
            Long idOrganizacao,
            GithubOrganizacaoConfig githubConfig,
            OrganizacaoGithubIntegracao integracao,
            GithubIntegracaoSettings integracaoSettings,
            Long installationIdWebhook) {
        String action = texto(root, "action");
        if (!Set.of("edited", "reordered", "deleted").contains(action)) {
            return Optional.empty();
        }

        JsonNode changes = root.get("changes");
        String statusDestino = resolverStatusProjectsV2(action, changes);
        String statusAnterior = extrairStatusAnteriorDeChanges(changes);
        String contexto = contextoProjectsV2(action);

        JsonNode issue = root.get("issue");
        JsonNode item = root.get("projects_v2_item");
        boolean pullRequest = ehPullRequestProjectV2(root);
        boolean issueProjectV2 = ehIssueProjectV2(root);
        JsonNode pullRequestNode = root.get("pull_request");

        String titulo;
        String url;
        Integer numero = extrairNumeroIssue(issue);
        if (pullRequest && pullRequestNode != null && !pullRequestNode.isNull()) {
            titulo = texto(pullRequestNode, "title");
            if (!StringUtils.hasText(titulo)) {
                titulo = tituloProjectsV2(issue, item);
            }
            url = texto(pullRequestNode, "html_url");
            if (numero == null) {
                numero = extrairNumeroIssue(pullRequestNode);
            }
            contexto = "Pull Request atualizado no Project (v2)";
        } else {
            titulo = tituloProjectsV2(issue, item);
            url = issue != null && !issue.isNull() ? texto(issue, "html_url") : null;
        }

        List<String> logins = extrairLoginsAssignees(issue);

        if (item != null && !item.isNull()) {
            String contentNodeId = texto(item, "content_node_id");
            String contentType = texto(item, "content_type");
            if (StringUtils.hasText(contentNodeId)) {
                Optional<String> bearer = githubGraphqlAccessTokenResolver.resolverBearer(
                        idOrganizacao, integracao, integracaoSettings, installationIdWebhook);
                if (bearer.isEmpty()) {
                    log.warn(
                            "GitHub GraphQL ignorado (sem token App/PAT) org={} nodeId={} — "
                                    + "assignees do Project v2 podem ficar vazios no webhook",
                            idOrganizacao,
                            contentNodeId);
                } else {
                    Optional<GithubProjectV2ContentDetalhes> detalhes = githubGraphqlContentResolver.enriquecer(
                            idOrganizacao,
                            integracaoSettings,
                            bearer.get(),
                            contentNodeId,
                            contentType);
                    if (detalhes.isEmpty()) {
                        log.warn(
                                "GitHub GraphQL nao enriqueceu org={} nodeId={} contentType={}",
                                idOrganizacao,
                                contentNodeId,
                                contentType);
                    } else {
                        GithubProjectV2ContentDetalhes graphql = detalhes.get();
                        if (StringUtils.hasText(graphql.url())) {
                            url = graphql.url();
                        }
                        if (StringUtils.hasText(graphql.titulo())) {
                            titulo = graphql.titulo();
                        }
                        if (graphql.numero() != null) {
                            numero = graphql.numero();
                        }
                        logins = combinarAssignees(logins, graphql.assigneeLogins());
                        if (graphql.assigneeLogins().isEmpty()) {
                            log.info(
                                    "GitHub GraphQL sem assignees org={} nodeId={} typename={}",
                                    idOrganizacao,
                                    contentNodeId,
                                    graphql.contentTypename());
                        }
                    }
                }
            }
        }

        return Optional.of(eventoDados(
                titulo,
                statusDestino,
                statusAnterior,
                contexto,
                action,
                url,
                root,
                logins,
                pullRequest,
                issueProjectV2,
                numero));
    }

    private boolean ehIssueProjectV2(JsonNode root) {
        if (root == null || root.isNull()) {
            return false;
        }
        JsonNode item = root.get("projects_v2_item");
        if (item == null || item.isNull()) {
            return false;
        }
        String contentType = texto(item, "content_type");
        if (!StringUtils.hasText(contentType)) {
            return false;
        }
        return "issue".equalsIgnoreCase(contentType.trim());
    }

    private boolean ehPullRequestProjectV2(JsonNode root) {
        if (root == null || root.isNull()) {
            return false;
        }
        JsonNode pullRequest = root.get("pull_request");
        if (pullRequest != null && !pullRequest.isNull()) {
            return true;
        }
        JsonNode item = root.get("projects_v2_item");
        if (item == null || item.isNull()) {
            return false;
        }
        String contentType = texto(item, "content_type");
        if (!StringUtils.hasText(contentType)) {
            return false;
        }
        String normalizado = contentType.replace("_", "").replace(" ", "").toLowerCase(Locale.ROOT);
        return "pullrequest".equals(normalizado);
    }

    private String contextoProjectsV2(String action) {
        return switch (action) {
            case "edited" -> "Item editado no Project (v2)";
            case "reordered" -> "Item reordenado no Project (v2)";
            case "deleted" -> "Item removido do Project (v2)";
            default -> "Item atualizado no Project (v2)";
        };
    }

    private String resolverStatusProjectsV2(String action, JsonNode changes) {
        if ("deleted".equals(action)) {
            return "Removido";
        }

        String statusColuna = extrairStatusColunaDeChanges(changes);
        if (StringUtils.hasText(statusColuna)) {
            return statusColuna;
        }

        return switch (action) {
            case "reordered" -> "Reordenado";
            case "edited" -> "Editado";
            default -> action;
        };
    }

    private String extrairStatusColunaDeChanges(JsonNode changes) {
        if (changes == null || changes.isNull()) {
            return null;
        }
        JsonNode fieldValue = changes.get("field_value");
        if (fieldValue == null || fieldValue.isNull()) {
            return null;
        }
        JsonNode to = fieldValue.get("to");
        if (to == null || to.isNull()) {
            return null;
        }
        return texto(to, "name");
    }

    private String extrairStatusAnteriorDeChanges(JsonNode changes) {
        if (changes == null || changes.isNull()) {
            return null;
        }
        JsonNode fieldValue = changes.get("field_value");
        if (fieldValue == null || fieldValue.isNull()) {
            return null;
        }
        JsonNode from = fieldValue.get("from");
        if (from == null || from.isNull()) {
            return null;
        }
        return texto(from, "name");
    }

    private String tituloProjectsV2(JsonNode issue, JsonNode item) {
        if (issue != null && !issue.isNull()) {
            String issueTitle = texto(issue, "title");
            if (StringUtils.hasText(issueTitle)) {
                return issueTitle;
            }
        }
        if (item != null && !item.isNull()) {
            String contentType = texto(item, "content_type");
            if (StringUtils.hasText(contentType)) {
                return "Item " + contentType + " no Project";
            }
        }
        return "Item do Project";
    }

    private Optional<MensagemKanban> extrairIssue(JsonNode root) {
        String action = texto(root, "action");
        if (!Set.of("assigned", "unassigned", "closed", "opened", "reopened", "labeled").contains(action)) {
            return Optional.empty();
        }

        JsonNode issue = root.get("issue");
        if (issue == null || issue.isNull()) {
            return Optional.empty();
        }

        String titulo = texto(issue, "title");
        String url = texto(issue, "html_url");
        String statusDestino = action;
        List<String> logins = extrairLoginsAssignees(issue);

        if ("assigned".equals(action)) {
            JsonNode assignee = root.get("assignee");
            if (assignee != null && !assignee.isNull()) {
                String loginAtribuido = texto(assignee, "login");
                if (StringUtils.hasText(loginAtribuido)) {
                    logins = List.of(loginAtribuido.toLowerCase(Locale.ROOT));
                }
            }
        }

        return Optional.of(eventoDados(
                titulo,
                statusDestino,
                null,
                "Issue atualizada",
                action,
                url,
                root,
                logins,
                false,
                false,
                extrairNumeroIssue(issue)));
    }

    /**
     * Assignees do GraphQL (issue/PR via {@code content_node_id}) sao a fonte padrao no Project v2.
     * O payload do webhook so entra quando o GraphQL nao trouxe assignees.
     */
    private List<String> combinarAssignees(List<String> doPayload, List<String> doGraphql) {
        if (doGraphql != null && !doGraphql.isEmpty()) {
            return List.copyOf(doGraphql);
        }
        return doPayload != null ? doPayload : List.of();
    }

    private Integer extrairNumeroIssue(JsonNode issue) {
        if (issue == null || issue.isNull() || !issue.has("number")) {
            return null;
        }
        JsonNode valor = issue.get("number");
        if (valor == null || valor.isNull() || !valor.isNumber()) {
            return null;
        }
        return valor.asInt();
    }

    private List<String> extrairLoginsAssignees(JsonNode issue) {
        if (issue == null || issue.isNull()) {
            return List.of();
        }

        List<String> logins = new ArrayList<>();
        JsonNode assignee = issue.get("assignee");
        if (assignee != null && !assignee.isNull()) {
            adicionarLogin(logins, texto(assignee, "login"));
        }

        JsonNode assignees = issue.get("assignees");
        if (assignees != null && assignees.isArray()) {
            for (JsonNode item : assignees) {
                adicionarLogin(logins, texto(item, "login"));
            }
        }

        return logins;
    }

    private void adicionarLogin(List<String> logins, String login) {
        if (!StringUtils.hasText(login)) {
            return;
        }
        String normalizado = login.trim().toLowerCase(Locale.ROOT);
        if (!logins.contains(normalizado)) {
            logins.add(normalizado);
        }
    }

    private MensagemKanban eventoDados(
            String titulo,
            String statusDestino,
            String contexto,
            String acao,
            String url,
            JsonNode root,
            List<String> logins,
            boolean pullRequest) {
        return eventoDados(titulo, statusDestino, null, contexto, acao, url, root, logins, pullRequest, false, null);
    }

    private MensagemKanban eventoDados(
            String titulo,
            String statusDestino,
            String statusAnterior,
            String contexto,
            String acao,
            String url,
            JsonNode root,
            List<String> logins,
            boolean pullRequest,
            boolean issueProjectV2,
            Integer numero) {
        return new MensagemKanban(
                titulo,
                statusDestino,
                statusAnterior,
                contexto,
                acao,
                url,
                extrairSenderLogin(root),
                logins,
                pullRequest,
                issueProjectV2,
                numero);
    }

    private String extrairSenderLogin(JsonNode root) {
        if (root == null || root.isNull()) {
            return null;
        }
        JsonNode sender = root.get("sender");
        if (sender == null || sender.isNull()) {
            return null;
        }
        return texto(sender, "login");
    }

    private List<String> resolverTelefonesDestino(Long idOrganizacao, List<String> loginsResponsaveis) {
        Set<String> telefones = new LinkedHashSet<>();

        for (String login : loginsResponsaveis) {
            githubResponsavelService
                    .buscarWhatsappPorLogin(idOrganizacao, login)
                    .map(String::trim)
                    .filter(StringUtils::hasText)
                    .ifPresent(telefones::add);
        }

        return List.copyOf(telefones);
    }

    private boolean statusPermitido(String statusDisparoConfig, String statusDestino) {
        if (!StringUtils.hasText(statusDisparoConfig)) {
            return true;
        }
        if (!StringUtils.hasText(statusDestino)) {
            return false;
        }
        Set<String> permitidos = Arrays.stream(statusDisparoConfig.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(this::normalizarStatusComparacao)
                .collect(Collectors.toSet());
        String destinoNorm = normalizarStatusComparacao(statusDestino);
        if (permitidos.contains(destinoNorm)) {
            return true;
        }
        String sufixoParenteses = extrairTextoEntreParentesesFinal(destinoNorm);
        if (StringUtils.hasText(sufixoParenteses) && permitidos.contains(sufixoParenteses)) {
            return true;
        }
        for (String permitido : permitidos) {
            if (destinoNorm.endsWith("(" + permitido + ")")) {
                return true;
            }
        }
        return false;
    }

    private String extrairTextoEntreParentesesFinal(String textoNormalizado) {
        if (!StringUtils.hasText(textoNormalizado)) {
            return null;
        }
        int open = textoNormalizado.lastIndexOf('(');
        int close = textoNormalizado.lastIndexOf(')');
        if (open < 0 || close <= open) {
            return null;
        }
        return textoNormalizado.substring(open + 1, close).trim();
    }

    private String normalizarStatusComparacao(String texto) {
        String semAcentos = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return semAcentos.toLowerCase(Locale.ROOT).trim();
    }

    private String texto(JsonNode node, String field) {
        if (node == null || node.isNull()) {
            return null;
        }
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        return value.asText(null);
    }

    private record MensagemKanban(
            String titulo,
            String statusDestino,
            String statusAnterior,
            String contexto,
            String acao,
            String url,
            String senderLogin,
            List<String> githubLogins,
            boolean pullRequest,
            boolean issueProjectV2,
            Integer numero) {
    }
}
