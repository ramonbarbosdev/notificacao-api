package com.notificacao_api.service.github;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
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
            GithubGraphqlContentResolver githubGraphqlContentResolver) {
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
    }

    public void processar(Long idOrganizacao, String githubEvent, String deliveryId, String payloadJson) {
        featureFlagService.validarRecursoHabilitado(idOrganizacao, RecursoFeature.GITHUB_WEBHOOK);

        OrganizacaoConfiguracao configuracao = configuracaoRepository.findByIdOrganizacao(idOrganizacao)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT, "Configuracao da organizacao nao encontrada."));

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

        if ("ping".equalsIgnoreCase(evento)) {
            log.info("GitHub webhook ping recebido org={} delivery={}", idOrganizacao, deliveryId);
            return;
        }

        Long installationIdWebhook = extrairInstallationId(root);
        sincronizarInstallationId(configuracao, installationIdWebhook);
        GithubIntegracaoSettings integracaoSettings = githubIntegracaoSettingsService.resolver(configuracao);

        Optional<MensagemKanban> mensagem = extrairMensagem(
                evento, root, idOrganizacao, configuracao, integracaoSettings, installationIdWebhook);
        if (mensagem.isEmpty()) {
            log.info(
                    "GitHub webhook ignorado (evento/acao nao tratado) org={} event={} action={} delivery={}",
                    idOrganizacao,
                    evento,
                    texto(root, "action"),
                    deliveryId);
            return;
        }

        MensagemKanban dados = mensagem.get();

        Set<Gatilho> gatilhos = GithubWebhookRegrasNotificacao.classificarGatilhos(
                configuracao, evento, dados.acao(), root);
        if (!GithubWebhookRegrasNotificacao.deveNotificarPorGatilho(configuracao, gatilhos)) {
            log.info(
                    "GitHub webhook ignorado por regras de notificacao org={} event={} action={} gatilhos={} delivery={}",
                    idOrganizacao,
                    evento,
                    dados.acao(),
                    gatilhos,
                    deliveryId);
            return;
        }

        boolean avisoPrAvaliadores = Boolean.TRUE.equals(configuracao.getGithubPrAvisarAvaliadores())
                && dados.pullRequest()
                && statusPermitido(configuracao.getDsGithubPrStatusDisparo(), dados.statusDestino());

        if (!avisoPrAvaliadores
                && !statusPermitido(configuracao.getDsGithubStatusDisparo(), dados.statusDestino())) {
            log.info(
                    "GitHub webhook ignorado por filtro de status org={} status={} filtro={} delivery={}",
                    idOrganizacao,
                    dados.statusDestino(),
                    configuracao.getDsGithubStatusDisparo(),
                    deliveryId);
            return;
        }

        List<String> loginsResponsaveis;
        if (avisoPrAvaliadores) {
            loginsResponsaveis =
                    GithubWebhookRegrasNotificacao.parseLoginsLista(configuracao.getDsGithubPrLoginsAvaliadores());
            log.info(
                    "GitHub webhook PR avaliadores org={} event={} delivery={} status={} logins={}",
                    idOrganizacao,
                    evento,
                    deliveryId,
                    dados.statusDestino(),
                    loginsResponsaveis);
        } else {
            loginsResponsaveis = GithubWebhookRegrasNotificacao.resolverLoginsDestino(
                    configuracao, evento, root, dados.githubLogins(), dados.senderLogin());
            log.info(
                    "GitHub webhook analisado org={} event={} delivery={} status={} logins={}",
                    idOrganizacao,
                    evento,
                    deliveryId,
                    dados.statusDestino(),
                    loginsResponsaveis);
        }

        List<String> telefonesDestino = resolverTelefonesDestino(idOrganizacao, loginsResponsaveis);

        String referencia = deliveryId != null && !deliveryId.isBlank()
                ? "github:" + deliveryId
                : null;

        GithubWebhookWhatsappTemplateService.GithubWebhookEventoDados eventoTemplate =
                new GithubWebhookWhatsappTemplateService.GithubWebhookEventoDados(
                        dados.titulo(),
                        dados.statusDestino(),
                        dados.contexto(),
                        dados.acao(),
                        dados.url(),
                        dados.senderLogin(),
                        loginsResponsaveis,
                        dados.numero());

        GithubWebhookWhatsappTemplateService.MensagemWhatsapp mensagemWhatsapp = whatsappTemplateService.formatar(
                configuracao,
                evento,
                deliveryId,
                eventoTemplate);

        EnviarNotificacaoRequisicao requisicaoBase = new EnviarNotificacaoRequisicao(
                CanalNotificacao.WHATSAPP,
                "",
                mensagemWhatsapp.assunto(),
                mensagemWhatsapp.textoWhatsapp(),
                null,
                null,
                referencia);

        if (telefonesDestino.isEmpty()) {
            if (!organizacaoConfiguracaoService.deveRegistrarFilaSemDestinatario(configuracao)) {
                log.info(
                        "GitHub webhook ignorado sem responsavel com opt-in org={} event={} delivery={} logins={}",
                        idOrganizacao,
                        evento,
                        deliveryId,
                        loginsResponsaveis);
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

    private void sincronizarInstallationId(OrganizacaoConfiguracao configuracao, Long installationIdWebhook) {
        if (installationIdWebhook == null || installationIdWebhook <= 0) {
            return;
        }
        if (configuracao.getNuGithubInstallationId() != null) {
            return;
        }
        configuracao.setNuGithubInstallationId(installationIdWebhook);
        configuracaoRepository.save(configuracao);
        log.info(
                "GitHub installation id persistido automaticamente org={} installationId={}",
                configuracao.getIdOrganizacao(),
                installationIdWebhook);
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
            OrganizacaoConfiguracao configuracao,
            GithubIntegracaoSettings integracaoSettings,
            Long installationIdWebhook) {
        if ("project_card".equals(githubEvent)) {
            return extrairProjectCard(root);
        }
        if ("projects_v2_item".equals(githubEvent)) {
            return extrairProjectsV2Item(
                    root, idOrganizacao, configuracao, integracaoSettings, installationIdWebhook);
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
            OrganizacaoConfiguracao configuracao,
            GithubIntegracaoSettings integracaoSettings,
            Long installationIdWebhook) {
        String action = texto(root, "action");
        if (!Set.of("edited", "reordered", "deleted").contains(action)) {
            return Optional.empty();
        }

        JsonNode changes = root.get("changes");
        String statusDestino = resolverStatusProjectsV2(action, changes);
        String contexto = contextoProjectsV2(action);

        JsonNode issue = root.get("issue");
        JsonNode item = root.get("projects_v2_item");
        boolean pullRequest = ehPullRequestProjectV2(root);
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
                        idOrganizacao, configuracao, integracaoSettings, installationIdWebhook);
                Optional<GithubProjectV2ContentDetalhes> detalhes = bearer.flatMap(token ->
                        githubGraphqlContentResolver.enriquecer(
                                idOrganizacao,
                                integracaoSettings,
                                token,
                                contentNodeId,
                                contentType));
                if (detalhes.isPresent()) {
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
                }
            }
        }

        return Optional.of(eventoDados(
                titulo,
                statusDestino,
                contexto,
                action,
                url,
                root,
                logins,
                pullRequest,
                numero));
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
        String normalizado = contentType.replace("_", "").toLowerCase(Locale.ROOT);
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
                "Issue atualizada",
                action,
                url,
                root,
                logins,
                false,
                extrairNumeroIssue(issue)));
    }

    private List<String> combinarAssignees(List<String> doPayload, List<String> doGraphql) {
        if (doGraphql == null || doGraphql.isEmpty()) {
            return doPayload != null ? doPayload : List.of();
        }
        if (doPayload == null || doPayload.isEmpty()) {
            return List.copyOf(doGraphql);
        }
        LinkedHashSet<String> unidos = new LinkedHashSet<>(doPayload);
        unidos.addAll(doGraphql);
        return List.copyOf(unidos);
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
        return eventoDados(titulo, statusDestino, contexto, acao, url, root, logins, pullRequest, null);
    }

    private MensagemKanban eventoDados(
            String titulo,
            String statusDestino,
            String contexto,
            String acao,
            String url,
            JsonNode root,
            List<String> logins,
            boolean pullRequest,
            Integer numero) {
        return new MensagemKanban(
                titulo,
                statusDestino,
                contexto,
                acao,
                url,
                extrairSenderLogin(root),
                logins,
                pullRequest,
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
        return permitidos.contains(normalizarStatusComparacao(statusDestino));
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
            String contexto,
            String acao,
            String url,
            String senderLogin,
            List<String> githubLogins,
            boolean pullRequest,
            Integer numero) {

        GithubWebhookWhatsappTemplateService.GithubWebhookEventoDados toEventoDados() {
            return new GithubWebhookWhatsappTemplateService.GithubWebhookEventoDados(
                    titulo, statusDestino, contexto, acao, url, senderLogin, githubLogins, numero);
        }
    }
}
