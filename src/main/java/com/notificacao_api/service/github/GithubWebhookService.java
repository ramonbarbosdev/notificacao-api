package com.notificacao_api.service.github;

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

@Service
public class GithubWebhookService {

    private static final Logger log = LoggerFactory.getLogger(GithubWebhookService.class);

    private final FeatureFlagService featureFlagService;
    private final OrganizacaoConfiguracaoRepository configuracaoRepository;
    private final OrganizacaoGithubResponsavelService githubResponsavelService;
    private final ObjectMapper objectMapper;
    private final NotificacaoService notificacaoService;

    public GithubWebhookService(
            FeatureFlagService featureFlagService,
            OrganizacaoConfiguracaoRepository configuracaoRepository,
            OrganizacaoGithubResponsavelService githubResponsavelService,
            ObjectMapper objectMapper,
            NotificacaoService notificacaoService) {
        this.featureFlagService = featureFlagService;
        this.configuracaoRepository = configuracaoRepository;
        this.githubResponsavelService = githubResponsavelService;
        this.objectMapper = objectMapper;
        this.notificacaoService = notificacaoService;
    }

    public void processar(Long idOrganizacao, String githubEvent, String deliveryId, String payloadJson) {
        featureFlagService.validarRecursoHabilitado(idOrganizacao, RecursoFeature.GITHUB_WEBHOOK);

        OrganizacaoConfiguracao configuracao = configuracaoRepository.findByIdOrganizacao(idOrganizacao)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT, "Configuracao da organizacao nao encontrada."));

        if ("ping".equalsIgnoreCase(githubEvent)) {
            log.info("GitHub webhook ping recebido org={} delivery={}", idOrganizacao, deliveryId);
            return;
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(payloadJson);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payload JSON invalido.");
        }

        Optional<MensagemKanban> mensagem = extrairMensagem(githubEvent, root);
        if (mensagem.isEmpty()) {
            log.debug("Evento GitHub ignorado org={} event={} delivery={}", idOrganizacao, githubEvent, deliveryId);
            return;
        }

        MensagemKanban dados = mensagem.get();
        if (!statusPermitido(configuracao.getDsGithubStatusDisparo(), dados.statusDestino())) {
            log.debug(
                    "Status/coluna nao configurado para disparo org={} status={} delivery={}",
                    idOrganizacao,
                    dados.statusDestino(),
                    deliveryId);
            return;
        }

        List<String> loginsResponsaveis = dados.githubLogins();
        if (loginsResponsaveis.isEmpty()) {
            log.info(
                    "GitHub webhook sem assignee no payload org={} event={} delivery={}",
                    idOrganizacao,
                    githubEvent,
                    deliveryId);
            return;
        }

        String referencia = deliveryId != null && !deliveryId.isBlank()
                ? "github:" + deliveryId
                : null;

        Set<String> telefonesEnviados = new LinkedHashSet<>();
        int enfileirados = 0;

        for (String login : loginsResponsaveis) {
            Optional<String> whatsapp = githubResponsavelService.buscarWhatsappPorLogin(idOrganizacao, login);
            if (whatsapp.isEmpty()) {
                log.warn(
                        "WhatsApp nao cadastrado para responsavel GitHub org={} login={} delivery={}",
                        idOrganizacao,
                        login,
                        deliveryId);
                continue;
            }

            String destinatario = whatsapp.get().trim();
            if (!telefonesEnviados.add(destinatario)) {
                continue;
            }

            EnviarNotificacaoRequisicao requisicao = new EnviarNotificacaoRequisicao(
                    CanalNotificacao.WHATSAPP,
                    destinatario,
                    "GitHub: " + dados.titulo(),
                    dados.corpo(),
                    null,
                    null,
                    referencia);

            notificacaoService.enviarParaOrganizacao(idOrganizacao, requisicao);
            enfileirados++;
        }

        if (enfileirados == 0) {
            log.warn(
                    "Nenhum WhatsApp enfileirado: assignee sem opt-in GitHub org={} logins={} delivery={}",
                    idOrganizacao,
                    loginsResponsaveis,
                    deliveryId);
            return;
        }

        log.info(
                "WhatsApp enfileirado via GitHub webhook org={} event={} delivery={} destinatarios={}",
                idOrganizacao,
                githubEvent,
                deliveryId,
                enfileirados);
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

        String corpo = montarCorpo(
                "Cartao movido no Project (classico)",
                titulo,
                coluna,
                url,
                action,
                logins);

        return Optional.of(new MensagemKanban(titulo, coluna, corpo, logins));
    }

    private Optional<MensagemKanban> extrairProjectsV2Item(JsonNode root) {
        String action = texto(root, "action");
        if (!"edited".equals(action) && !"created".equals(action) && !"reordered".equals(action)) {
            return Optional.empty();
        }

        JsonNode changes = root.get("changes");
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

        String corpo = montarCorpo(
                "Item atualizado no Project (v2)",
                titulo,
                statusDestino,
                url,
                action,
                logins);

        return Optional.of(new MensagemKanban(titulo, statusDestino, corpo, logins));
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

        String corpo = montarCorpo(
                "Issue atualizada",
                titulo,
                statusDestino,
                url,
                action,
                logins);

        return Optional.of(new MensagemKanban(titulo, statusDestino, corpo, logins));
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

    private String montarCorpo(
            String contexto,
            String titulo,
            String statusOuColuna,
            String url,
            String action,
            List<String> logins) {
        StringBuilder sb = new StringBuilder();
        sb.append(contexto).append('\n');
        sb.append("Acao: ").append(action).append('\n');
        sb.append("Titulo: ").append(titulo != null ? titulo : "-").append('\n');
        sb.append("Status/Coluna: ").append(statusOuColuna != null ? statusOuColuna : "-");
        if (!logins.isEmpty()) {
            sb.append('\n').append("Responsavel(is): @").append(String.join(", @", logins));
        }
        if (StringUtils.hasText(url)) {
            sb.append('\n').append(url);
        }
        return sb.toString().trim();
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
                .map(s -> s.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        return permitidos.contains(statusDestino.toLowerCase(Locale.ROOT));
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

    private record MensagemKanban(String titulo, String statusDestino, String corpo, List<String> githubLogins) {
    }
}
