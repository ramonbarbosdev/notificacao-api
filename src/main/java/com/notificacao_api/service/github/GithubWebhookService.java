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

    public GithubWebhookService(
            FeatureFlagService featureFlagService,
            OrganizacaoConfiguracaoRepository configuracaoRepository,
            OrganizacaoGithubResponsavelService githubResponsavelService,
            ObjectMapper objectMapper,
            NotificacaoService notificacaoService,
            OrganizacaoConfiguracaoService organizacaoConfiguracaoService,
            GithubWebhookWhatsappTemplateService whatsappTemplateService) {
        this.featureFlagService = featureFlagService;
        this.configuracaoRepository = configuracaoRepository;
        this.githubResponsavelService = githubResponsavelService;
        this.objectMapper = objectMapper;
        this.notificacaoService = notificacaoService;
        this.organizacaoConfiguracaoService = organizacaoConfiguracaoService;
        this.whatsappTemplateService = whatsappTemplateService;
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

        Optional<MensagemKanban> mensagem = extrairMensagem(evento, root);
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
        if (!statusPermitido(configuracao.getDsGithubStatusDisparo(), dados.statusDestino())) {
            log.info(
                    "GitHub webhook ignorado por filtro de status org={} status={} filtro={} delivery={}",
                    idOrganizacao,
                    dados.statusDestino(),
                    configuracao.getDsGithubStatusDisparo(),
                    deliveryId);
            return;
        }

        List<String> loginsResponsaveis = resolverLoginsDestino(evento, root, dados.githubLogins());
        log.info(
                "GitHub webhook analisado org={} event={} delivery={} status={} logins={}",
                idOrganizacao,
                evento,
                deliveryId,
                dados.statusDestino(),
                loginsResponsaveis);

        List<String> telefonesDestino = resolverTelefonesDestino(idOrganizacao, loginsResponsaveis);

        String referencia = deliveryId != null && !deliveryId.isBlank()
                ? "github:" + deliveryId
                : null;

        GithubWebhookWhatsappTemplateService.MensagemWhatsapp mensagemWhatsapp = whatsappTemplateService.formatar(
                configuracao,
                evento,
                deliveryId,
                dados.toEventoDados());

        EnviarNotificacaoRequisicao requisicaoBase = new EnviarNotificacaoRequisicao(
                CanalNotificacao.WHATSAPP,
                "",
                mensagemWhatsapp.assunto(),
                mensagemWhatsapp.mensagem(),
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

    private Optional<MensagemKanban> extrairMensagem(String githubEvent, JsonNode root) {
        if ("project_card".equals(githubEvent)) {
            return extrairProjectCard(root);
        }
        if ("projects_v2_item".equals(githubEvent)) {
            return extrairProjectsV2Item(root);
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
                logins));
    }

    private Optional<MensagemKanban> extrairProjectsV2Item(JsonNode root) {
        String action = texto(root, "action");
        if (!"edited".equals(action) && !"created".equals(action) && !"reordered".equals(action)) {
            return Optional.empty();
        }

        JsonNode changes = root.get("changes");
        if ("reordered".equals(action) && (changes == null || changes.isNull() || !changes.has("field_value"))) {
            return Optional.empty();
        }
        String statusDestino = null;
        if (changes != null && !changes.isNull()) {
            JsonNode fieldValue = changes.get("field_value");
            if (fieldValue != null && !fieldValue.isNull()) {
                JsonNode to = fieldValue.get("to");
                if (to != null && !to.isNull()) {
                    statusDestino = texto(to, "name");
                }
            }
        }

        if (!StringUtils.hasText(statusDestino)) {
            statusDestino = action;
        }

        JsonNode issue = root.get("issue");
        String titulo = "Item do Project";
        if (issue != null && !issue.isNull()) {
            String issueTitle = texto(issue, "title");
            if (StringUtils.hasText(issueTitle)) {
                titulo = issueTitle;
            }
        } else {
            JsonNode item = root.get("projects_v2_item");
            if (item != null && !item.isNull()) {
                String contentType = texto(item, "content_type");
                if (StringUtils.hasText(contentType)) {
                    titulo = "Item " + contentType + " no Project";
                }
            }
        }

        List<String> logins = extrairLoginsAssignees(issue);
        String url = issue != null ? texto(issue, "html_url") : null;

        return Optional.of(eventoDados(
                titulo,
                statusDestino,
                "Item atualizado no Project (v2)",
                action,
                url,
                root,
                logins));
    }

    private Optional<MensagemKanban> extrairIssue(JsonNode root) {
        String action = texto(root, "action");
        if (!Set.of("assigned", "closed", "opened", "reopened", "labeled").contains(action)) {
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
                logins));
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
            List<String> logins) {
        return new MensagemKanban(
                titulo,
                statusDestino,
                contexto,
                acao,
                url,
                extrairSenderLogin(root),
                logins);
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

    private List<String> resolverLoginsDestino(String githubEvent, JsonNode root, List<String> loginsPayload) {
        List<String> logins = new ArrayList<>(loginsPayload);
        if (!logins.isEmpty()) {
            return logins;
        }
        if ("projects_v2_item".equals(githubEvent) || "project_card".equals(githubEvent)) {
            JsonNode sender = root.get("sender");
            if (sender != null && !sender.isNull()) {
                adicionarLogin(logins, texto(sender, "login"));
            }
        }
        return logins;
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
            List<String> githubLogins) {

        GithubWebhookWhatsappTemplateService.GithubWebhookEventoDados toEventoDados() {
            return new GithubWebhookWhatsappTemplateService.GithubWebhookEventoDados(
                    titulo, statusDestino, contexto, acao, url, senderLogin, githubLogins);
        }
    }
}
