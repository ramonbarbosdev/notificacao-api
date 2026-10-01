package com.notificacao_api.service.github;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.databind.JsonNode;
import com.notificacao_api.dto.integracao.GithubKanbanMovimentacaoWebhookLotePayload;
import com.notificacao_api.dto.integracao.GithubKanbanMovimentacaoWebhookPayload;
import com.notificacao_api.dto.integracao.GithubKanbanWebhookPessoa;
import com.notificacao_api.enums.GithubKanbanWebhookModoEnvio;
import com.notificacao_api.service.github.OrganizacaoGithubKanbanMovimentacaoWebhookService.ConfiguracaoKanbanMovimentacaoWebhook;

@Service
public class GithubKanbanMovimentacaoWebhookService {

    private static final Logger log = LoggerFactory.getLogger(GithubKanbanMovimentacaoWebhookService.class);
    private static final DateTimeFormatter ISO_INSTANT = DateTimeFormatter.ISO_INSTANT;
    private static final int TIMEOUT_SEGUNDOS = 10;

    private final OrganizacaoGithubKanbanMovimentacaoWebhookService kanbanMovimentacaoWebhookConfigService;
    private final GithubKanbanWebhookFilaService filaService;
    private final GithubKanbanWebhookNomeEnriquecimentoService nomeEnriquecimentoService;
    private final RestClient restClient;

    public GithubKanbanMovimentacaoWebhookService(
            OrganizacaoGithubKanbanMovimentacaoWebhookService kanbanMovimentacaoWebhookConfigService,
            GithubKanbanWebhookFilaService filaService,
            GithubKanbanWebhookNomeEnriquecimentoService nomeEnriquecimentoService,
            RestClient.Builder restClientBuilder) {
        this.kanbanMovimentacaoWebhookConfigService = kanbanMovimentacaoWebhookConfigService;
        this.filaService = filaService;
        this.nomeEnriquecimentoService = nomeEnriquecimentoService;
        this.restClient = restClientBuilder
                .requestFactory(criarRequestFactory(TIMEOUT_SEGUNDOS))
                .build();
    }

    @Async
    public void publicarMovimentacaoAsync(
            Long idOrganizacao,
            String deliveryId,
            String githubEvent,
            JsonNode root,
            GithubWebhookService.MensagemKanban dados,
            List<String> responsaveis) {
        ConfiguracaoKanbanMovimentacaoWebhook config = kanbanMovimentacaoWebhookConfigService.resolver(idOrganizacao);
        if (!config.prontaParaEnvio()) {
            return;
        }
        if (!GithubWebhookService.ehMovimentacaoKanbanParaWebhookExterno(githubEvent, dados, root)) {
            return;
        }

        Map<String, String> cacheNomes = new HashMap<>();
        GithubKanbanMovimentacaoWebhookPayload payload =
                montarPayload(idOrganizacao, root, dados, responsaveis, cacheNomes);
        String chaveDedup = chaveDedup(idOrganizacao, payload);

        if (config.modoEnvio() == GithubKanbanWebhookModoEnvio.LOTE) {
            boolean enfileirado = filaService.enfileirar(idOrganizacao, deliveryId, chaveDedup, payload);
            if (enfileirado) {
                log.debug("Webhook kanban enfileirado org={} delivery={}", idOrganizacao, deliveryId);
            }
            return;
        }

        try {
            enviarComRetentativa(idOrganizacao, config, payload);
        } catch (Exception ex) {
            log.warn("Webhook kanban imediato falhou org={} motivo={}", idOrganizacao, resumirMotivo(ex));
        }
    }

    public boolean dispararEventoFicticio(Long idOrganizacao) {
        ConfiguracaoKanbanMovimentacaoWebhook config = kanbanMovimentacaoWebhookConfigService.resolver(idOrganizacao);
        if (!config.prontaParaEnvio()) {
            return false;
        }
        GithubKanbanWebhookPessoa movido = new GithubKanbanWebhookPessoa("bot-debug", "Bot Debug");
        GithubKanbanMovimentacaoWebhookPayload item = new GithubKanbanMovimentacaoWebhookPayload(
                "esimples-api",
                "issue",
                123,
                "Evento de teste — movimentação no kanban",
                "https://github.com/gpi-organizacao/esimples-api/issues/123",
                "Em Andamento",
                "Em revisão",
                "bot-debug",
                movido,
                List.of("octocat"),
                List.of(new GithubKanbanWebhookPessoa("octocat", "Octocat")),
                List.of("bug"),
                "alta",
                "2026-12-31",
                ISO_INSTANT.format(Instant.now().atOffset(ZoneOffset.UTC)));

        if (config.modoEnvio() == GithubKanbanWebhookModoEnvio.LOTE) {
            GithubKanbanMovimentacaoWebhookPayload item2 = new GithubKanbanMovimentacaoWebhookPayload(
                    "esimples-api",
                    "issue",
                    124,
                    "Segundo item de teste — lote",
                    "https://github.com/gpi-organizacao/esimples-api/issues/124",
                    "Em revisão",
                    "Concluído",
                    "bot-debug",
                    movido,
                    List.of("octocat"),
                    List.of(new GithubKanbanWebhookPessoa("octocat", "Octocat")),
                    List.of("teste"),
                    "media",
                    "2026-12-31",
                    ISO_INSTANT.format(Instant.now().atOffset(ZoneOffset.UTC)));
            LocalDateTime agora = LocalDateTime.now();
            return enviarLote(idOrganizacao, config, List.of(item, item2), agora.minusMinutes(5), agora);
        }

        try {
            enviarComRetentativa(idOrganizacao, config, item);
            return true;
        } catch (Exception ex) {
            log.warn("Webhook kanban teste imediato falhou org={} motivo={}", idOrganizacao, resumirMotivo(ex));
            return false;
        }
    }

    public boolean enviarLote(
            Long idOrganizacao,
            ConfiguracaoKanbanMovimentacaoWebhook config,
            List<GithubKanbanMovimentacaoWebhookPayload> movimentacoes,
            LocalDateTime periodoInicio,
            LocalDateTime periodoFim) {
        if (movimentacoes == null || movimentacoes.isEmpty()) {
            return false;
        }
        GithubKanbanMovimentacaoWebhookLotePayload lote = new GithubKanbanMovimentacaoWebhookLotePayload(
                "lote",
                idOrganizacao,
                GithubKanbanWebhookLoteAgendadoService.formatarInstant(periodoInicio),
                GithubKanbanWebhookLoteAgendadoService.formatarInstant(periodoFim),
                movimentacoes.size(),
                movimentacoes);
        try {
            enviarComRetentativa(idOrganizacao, config, lote);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    private void enviarComRetentativa(
            Long idOrganizacao, ConfiguracaoKanbanMovimentacaoWebhook config, Object body) {
        int tentativas = 2;
        for (int tentativa = 1; tentativa <= tentativas; tentativa++) {
            try {
                restClient.post()
                        .uri(config.url())
                        .header(HttpHeaders.AUTHORIZATION, config.authorizationHeader())
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .body(body)
                        .retrieve()
                        .toBodilessEntity();
                return;
            } catch (RestClientResponseException ex) {
                HttpStatusCode status = ex.getStatusCode();
                if (tentativa < tentativas && status.is5xxServerError()) {
                    continue;
                }
                log.warn(
                        "Webhook kanban movimentacao falhou org={} status={} motivo={}",
                        idOrganizacao,
                        status.value(),
                        resumirMotivo(ex));
                throw ex;
            } catch (Exception ex) {
                if (tentativa < tentativas) {
                    continue;
                }
                log.warn(
                        "Webhook kanban movimentacao falhou org={} status=timeout motivo={}",
                        idOrganizacao,
                        resumirMotivo(ex));
                throw new IllegalStateException(ex);
            }
        }
    }

    private static String resumirMotivo(Exception ex) {
        String mensagem = ex.getMessage();
        if (mensagem == null || mensagem.isBlank()) {
            return ex.getClass().getSimpleName();
        }
        if (mensagem.length() > 200) {
            return mensagem.substring(0, 200);
        }
        return mensagem;
    }

    private String chaveDedup(Long idOrganizacao, GithubKanbanMovimentacaoWebhookPayload payload) {
        String card = payload.repo() != null && payload.numero() != null
                ? payload.repo() + "#" + payload.numero()
                : payload.url();
        if (!StringUtils.hasText(card)) {
            return null;
        }
        String de = normalizarDedup(payload.de());
        String para = normalizarDedup(payload.para());
        return idOrganizacao + "|" + card + "|" + de + "|" + para;
    }

    private static String normalizarDedup(String texto) {
        return texto != null ? texto.trim().toLowerCase(Locale.ROOT) : "";
    }

    GithubKanbanMovimentacaoWebhookPayload montarPayload(
            Long idOrganizacao,
            JsonNode root,
            GithubWebhookService.MensagemKanban dados,
            List<String> responsaveis,
            Map<String, String> cacheNomes) {
        String repo = extrairRepo(root);
        String tipo = dados.pullRequest() ? "pull_request" : "issue";
        List<String> labels = extrairLabels(root);
        String prioridade = extrairCampoIssue(root, "priority");
        if (!StringUtils.hasText(prioridade)) {
            prioridade = extrairCampoCustomizadoChanges(root, "prioridade", "priority");
        }
        String targetDate = extrairCampoIssue(root, "target_date");
        if (!StringUtils.hasText(targetDate)) {
            targetDate = extrairCampoCustomizadoChanges(root, "target date", "target_date", "data");
        }

        GithubKanbanWebhookPessoa movidoPorDetalhe =
                nomeEnriquecimentoService.movidoPor(idOrganizacao, root, dados.senderLogin(), cacheNomes);
        List<GithubKanbanWebhookPessoa> responsaveisDetalhe =
                nomeEnriquecimentoService.responsaveis(idOrganizacao, root, responsaveis, cacheNomes, null);
        String movidoPorLogin = movidoPorDetalhe != null ? movidoPorDetalhe.login() : dados.senderLogin();

        return new GithubKanbanMovimentacaoWebhookPayload(
                repo,
                tipo,
                dados.numero(),
                dados.titulo(),
                dados.url(),
                dados.statusAnterior(),
                dados.statusDestino(),
                movidoPorLogin,
                movidoPorDetalhe,
                responsaveis != null && !responsaveis.isEmpty() ? List.copyOf(responsaveis) : null,
                responsaveisDetalhe,
                labels.isEmpty() ? null : labels,
                prioridade,
                targetDate,
                ISO_INSTANT.format(Instant.now().atOffset(ZoneOffset.UTC)));
    }

    private static List<String> extrairLabels(JsonNode root) {
        if (root == null || root.isNull()) {
            return List.of();
        }
        JsonNode issue = root.get("issue");
        if (issue == null || issue.isNull()) {
            issue = root.get("pull_request");
        }
        if (issue == null || issue.isNull()) {
            return List.of();
        }
        JsonNode labels = issue.get("labels");
        if (labels == null || !labels.isArray()) {
            return List.of();
        }
        List<String> nomes = new ArrayList<>();
        for (JsonNode label : labels) {
            if (label == null || label.isNull()) {
                continue;
            }
            String nome = label.isTextual() ? label.asText() : texto(label, "name");
            if (StringUtils.hasText(nome)) {
                nomes.add(nome.trim());
            }
        }
        return nomes;
    }

    private static String extrairRepo(JsonNode root) {
        if (root == null || root.isNull()) {
            return null;
        }
        JsonNode repository = root.get("repository");
        if (repository != null && !repository.isNull()) {
            String name = texto(repository, "name");
            if (StringUtils.hasText(name)) {
                return name.trim();
            }
        }
        JsonNode issue = root.get("issue");
        if (issue != null && !issue.isNull()) {
            JsonNode repo = issue.get("repository");
            if (repo != null && !repo.isNull()) {
                String name = texto(repo, "name");
                if (StringUtils.hasText(name)) {
                    return name.trim();
                }
            }
            String url = texto(issue, "html_url");
            String doUrl = repoDeHtmlUrl(url);
            if (StringUtils.hasText(doUrl)) {
                return doUrl;
            }
        }
        JsonNode pullRequest = root.get("pull_request");
        if (pullRequest != null && !pullRequest.isNull()) {
            String url = texto(pullRequest, "html_url");
            return repoDeHtmlUrl(url);
        }
        return null;
    }

    private static String repoDeHtmlUrl(String htmlUrl) {
        if (!StringUtils.hasText(htmlUrl)) {
            return null;
        }
        String[] partes = htmlUrl.split("/");
        if (partes.length >= 5 && "github.com".equalsIgnoreCase(partes[2].replace("www.", ""))) {
            return partes[4];
        }
        return null;
    }

    private static String extrairCampoIssue(JsonNode root, String field) {
        if (root == null) {
            return null;
        }
        JsonNode issue = root.get("issue");
        if (issue == null || issue.isNull()) {
            return null;
        }
        return texto(issue, field);
    }

    private static String extrairCampoCustomizadoChanges(JsonNode root, String... nomesCampo) {
        if (root == null || root.isNull()) {
            return null;
        }
        JsonNode changes = root.get("changes");
        if (changes == null || !changes.isObject()) {
            return null;
        }
        var fields = changes.fields();
        while (fields.hasNext()) {
            var entry = fields.next();
            String chave = entry.getKey().toLowerCase(Locale.ROOT);
            for (String nome : nomesCampo) {
                if (chave.contains(nome.toLowerCase(Locale.ROOT))) {
                    JsonNode to = entry.getValue().path("to");
                    String texto = to.isTextual() ? to.asText() : texto(to, "name");
                    if (StringUtils.hasText(texto)) {
                        return texto.trim();
                    }
                }
            }
        }
        return null;
    }

    private static String texto(JsonNode node, String field) {
        if (node == null || node.isNull()) {
            return null;
        }
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        return value.asText(null);
    }

    private static SimpleClientHttpRequestFactory criarRequestFactory(int timeoutSeconds) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        int millis = Math.max(1, timeoutSeconds) * 1000;
        factory.setConnectTimeout(millis);
        factory.setReadTimeout(millis);
        return factory;
    }
}
