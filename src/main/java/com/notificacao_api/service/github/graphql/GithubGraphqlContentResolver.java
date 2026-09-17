package com.notificacao_api.service.github.graphql;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.notificacao_api.service.github.GithubIntegracaoSettings;

@Service
public class GithubGraphqlContentResolver {

    private static final Logger log = LoggerFactory.getLogger(GithubGraphqlContentResolver.class);

    private static final String QUERY = """
            query($nodeId: ID!) {
              node(id: $nodeId) {
                __typename
                ... on Issue {
                  title
                  number
                  url
                  assignees(first: 20) {
                    nodes {
                      login
                      name
                    }
                  }
                }
                ... on PullRequest {
                  title
                  number
                  url
                  assignees(first: 20) {
                    nodes {
                      login
                      name
                    }
                  }
                }
                ... on DraftIssue { title }
              }
            }
            """;

    private final RestClient.Builder restClientBuilder;
    private final ObjectMapper objectMapper;

    public GithubGraphqlContentResolver(RestClient.Builder restClientBuilder, ObjectMapper objectMapper) {
        this.restClientBuilder = restClientBuilder;
        this.objectMapper = objectMapper;
    }

    public Optional<GithubProjectV2ContentDetalhes> enriquecer(
            Long idOrganizacao,
            GithubIntegracaoSettings settings,
            String token,
            String nodeId,
            String contentType) {
        if (settings == null || !StringUtils.hasText(token) || !StringUtils.hasText(nodeId)) {
            return Optional.empty();
        }

        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("query", QUERY);
            body.put("variables", Map.of("nodeId", nodeId.trim()));

            RestClient client = clientPara(settings);
            JsonNode resposta = executarGraphql(client, body, token);

            return parseResposta(idOrganizacao, nodeId, contentType, resposta);
        } catch (Exception ex) {
            log.warn(
                    "GitHub GraphQL enriquecimento falhou org={} nodeId={} contentType={} motivo={}",
                    idOrganizacao,
                    nodeId,
                    contentType,
                    ex.getMessage());
            return Optional.empty();
        }
    }

    public GithubGraphqlConsultaResult consultar(
            Long idOrganizacao,
            GithubIntegracaoSettings settings,
            String token,
            String nodeId,
            String contentType) {
        if (settings == null) {
            return GithubGraphqlConsultaResult.falha("Configuracao GraphQL indisponivel.");
        }
        if (!StringUtils.hasText(token)) {
            return GithubGraphqlConsultaResult.falha("Token GitHub App ou PAT nao configurado para esta organizacao.");
        }
        if (!StringUtils.hasText(nodeId)) {
            return GithubGraphqlConsultaResult.falha("Informe o content_node_id (nodeId).");
        }

        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("query", QUERY);
            body.put("variables", Map.of("nodeId", nodeId.trim()));

            RestClient client = clientPara(settings);
            JsonNode resposta = executarGraphql(client, body, token);

            if (resposta == null || resposta.isNull()) {
                return GithubGraphqlConsultaResult.falha("Resposta GraphQL vazia.");
            }

            List<String> errosGraphql = extrairErrosGraphql(resposta);
            if (!errosGraphql.isEmpty()) {
                return new GithubGraphqlConsultaResult(false, "GitHub GraphQL retornou erros.", errosGraphql, null);
            }

            Optional<GithubProjectV2ContentDetalhes> detalhes =
                    parseResposta(idOrganizacao, nodeId, contentType, resposta);
            if (detalhes.isEmpty()) {
                return new GithubGraphqlConsultaResult(
                        false,
                        "Node encontrado sem titulo, url, numero ou assignees.",
                        List.of(),
                        null);
            }
            return new GithubGraphqlConsultaResult(true, null, List.of(), detalhes.get());
        } catch (Exception ex) {
            log.warn(
                    "GitHub GraphQL consulta falhou org={} nodeId={} motivo={}",
                    idOrganizacao,
                    nodeId,
                    ex.getMessage());
            return GithubGraphqlConsultaResult.falha(ex.getMessage());
        }
    }

    private static List<String> extrairErrosGraphql(JsonNode resposta) {
        JsonNode errors = resposta.get("errors");
        if (errors == null || !errors.isArray() || errors.isEmpty()) {
            return List.of();
        }
        List<String> mensagens = new ArrayList<>();
        for (JsonNode item : errors) {
            if (item == null || item.isNull()) {
                continue;
            }
            String message = item.has("message") ? item.get("message").asText("") : item.toString();
            if (StringUtils.hasText(message)) {
                mensagens.add(message.trim());
            }
        }
        return List.copyOf(mensagens);
    }

    private RestClient clientPara(GithubIntegracaoSettings settings) {
        var httpFactory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        httpFactory.setConnectTimeout(Duration.ofMillis(settings.httpConnectTimeoutMs()));
        httpFactory.setReadTimeout(Duration.ofMillis(settings.httpReadTimeoutMs()));
        return restClientBuilder
                .baseUrl(settings.graphqlUrl())
                .requestFactory(httpFactory)
                .build();
    }

    private JsonNode executarGraphql(RestClient client, Map<String, Object> body, String token) throws Exception {
        String raw = client.post()
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token.trim())
                .body(body)
                .retrieve()
                .body(String.class);
        if (!StringUtils.hasText(raw)) {
            return objectMapper.nullNode();
        }
        return objectMapper.readTree(raw);
    }

    Optional<GithubProjectV2ContentDetalhes> parseResposta(
            Long idOrganizacao,
            String nodeId,
            String contentType,
            JsonNode resposta) {
        if (resposta == null || resposta.isNull()) {
            log.warn("GitHub GraphQL resposta vazia org={} nodeId={}", idOrganizacao, nodeId);
            return Optional.empty();
        }

        JsonNode errors = resposta.get("errors");
        if (errors != null && errors.isArray() && !errors.isEmpty()) {
            log.warn(
                    "GitHub GraphQL errors org={} nodeId={} contentType={} errors={}",
                    idOrganizacao,
                    nodeId,
                    contentType,
                    errors);
            return Optional.empty();
        }

        JsonNode node = resposta.path("data").path("node");
        if (node.isMissingNode() || node.isNull()) {
            log.warn("GitHub GraphQL node ausente org={} nodeId={}", idOrganizacao, nodeId);
            return Optional.empty();
        }

        String typename = texto(node, "__typename");
        String titulo = texto(node, "title");
        String url = texto(node, "url");
        Integer numero = numero(node);
        List<GithubGraphqlAssignee> assignees = parseAssignees(node);

        if (!StringUtils.hasText(titulo)
                && !StringUtils.hasText(url)
                && numero == null
                && assignees.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(new GithubProjectV2ContentDetalhes(titulo, url, typename, numero, assignees));
    }

    private static Integer numero(JsonNode node) {
        if (node == null || node.isNull() || !node.has("number")) {
            return null;
        }
        JsonNode valor = node.get("number");
        if (valor == null || valor.isNull() || !valor.isNumber()) {
            return null;
        }
        return valor.asInt();
    }

    private static List<GithubGraphqlAssignee> parseAssignees(JsonNode node) {
        if (node == null || node.isNull()) {
            return List.of();
        }
        JsonNode nodes = node.path("assignees").path("nodes");
        if (!nodes.isArray()) {
            return List.of();
        }
        List<GithubGraphqlAssignee> assignees = new ArrayList<>();
        for (JsonNode item : nodes) {
            if (item == null || item.isNull()) {
                continue;
            }
            String login = texto(item, "login");
            if (!StringUtils.hasText(login)) {
                continue;
            }
            assignees.add(new GithubGraphqlAssignee(login, texto(item, "name")));
        }
        return List.copyOf(assignees);
    }

    private static String texto(JsonNode node, String field) {
        if (node == null || node.isNull()) {
            return null;
        }
        JsonNode valor = node.get(field);
        if (valor == null || valor.isNull()) {
            return null;
        }
        String texto = valor.asText(null);
        return StringUtils.hasText(texto) ? texto.trim() : null;
    }
}
