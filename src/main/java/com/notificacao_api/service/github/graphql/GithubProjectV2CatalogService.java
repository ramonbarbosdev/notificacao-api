package com.notificacao_api.service.github.graphql;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.notificacao_api.dto.integracao.GithubProjectV2ResumoResponse;
import com.notificacao_api.dto.integracao.GithubProjectV2StatusOpcaoResponse;
import com.notificacao_api.service.github.GithubIntegracaoSettings;

@Service
public class GithubProjectV2CatalogService {

    private static final String QUERY_PROJECTS = """
            query($org: String!) {
              organization(login: $org) {
                projectsV2(first: 50) {
                  nodes {
                    id
                    number
                    title
                    url
                  }
                }
              }
            }
            """;

    private static final String QUERY_STATUS_OPTIONS = """
            query($projectId: ID!) {
              node(id: $projectId) {
                __typename
                ... on ProjectV2 {
                  id
                  number
                  title
                  url
                  fields(first: 50) {
                    nodes {
                      __typename
                      ... on ProjectV2SingleSelectField {
                        id
                        name
                        options {
                          id
                          name
                          color
                          description
                        }
                      }
                    }
                  }
                }
              }
            }
            """;

    private final RestClient.Builder restClientBuilder;
    private final ObjectMapper objectMapper;

    public GithubProjectV2CatalogService(RestClient.Builder restClientBuilder, ObjectMapper objectMapper) {
        this.restClientBuilder = restClientBuilder;
        this.objectMapper = objectMapper;
    }

    public CatalogResult<List<GithubProjectV2ResumoResponse>> listarProjects(
            GithubIntegracaoSettings settings, String token, String organizationLogin) {
        if (!StringUtils.hasText(organizationLogin)) {
            return CatalogResult.falha("Informe o login da organizacao GitHub (organization.login).");
        }
        if (!StringUtils.hasText(token)) {
            return CatalogResult.falha("Token GitHub App ou PAT nao configurado para esta organizacao.");
        }
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("query", QUERY_PROJECTS);
            body.put("variables", Map.of("org", organizationLogin.trim()));

            JsonNode resposta = executarGraphql(settings, token, body);
            List<String> erros = extrairErrosGraphql(resposta);
            if (!erros.isEmpty()) {
                return new CatalogResult<>(false, "GitHub GraphQL retornou erros.", erros, List.of());
            }
            return new CatalogResult<>(true, null, List.of(), parseProjects(resposta));
        } catch (Exception ex) {
            return CatalogResult.falha(ex.getMessage());
        }
    }

    public CatalogResult<StatusFieldResult> listarStatusOpcoes(
            GithubIntegracaoSettings settings, String token, String projectNodeId) {
        if (!StringUtils.hasText(projectNodeId)) {
            return CatalogResult.falha("Informe o node id do Project v2.");
        }
        if (!StringUtils.hasText(token)) {
            return CatalogResult.falha("Token GitHub App ou PAT nao configurado para esta organizacao.");
        }
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("query", QUERY_STATUS_OPTIONS);
            body.put("variables", Map.of("projectId", projectNodeId.trim()));

            JsonNode resposta = executarGraphql(settings, token, body);
            List<String> erros = extrairErrosGraphql(resposta);
            if (!erros.isEmpty()) {
                return new CatalogResult<>(false, "GitHub GraphQL retornou erros.", erros, null);
            }
            Optional<StatusFieldResult> parsed = parseStatusField(resposta);
            if (parsed.isEmpty()) {
                return CatalogResult.falha("Project v2 nao encontrado ou sem campo Status (SingleSelect).");
            }
            return new CatalogResult<>(true, null, List.of(), parsed.get());
        } catch (Exception ex) {
            return CatalogResult.falha(ex.getMessage());
        }
    }

    List<GithubProjectV2ResumoResponse> parseProjects(JsonNode resposta) {
        List<GithubProjectV2ResumoResponse> projects = new ArrayList<>();
        if (resposta == null || resposta.isNull()) {
            return projects;
        }
        JsonNode nodes = resposta.path("data").path("organization").path("projectsV2").path("nodes");
        if (!nodes.isArray()) {
            return projects;
        }
        for (JsonNode node : nodes) {
            if (node == null || node.isNull()) {
                continue;
            }
            String id = texto(node, "id");
            if (!StringUtils.hasText(id)) {
                continue;
            }
            Integer number = node.has("number") && node.get("number").isNumber()
                    ? node.get("number").asInt()
                    : null;
            projects.add(new GithubProjectV2ResumoResponse(
                    id, number, texto(node, "title"), texto(node, "url")));
        }
        return List.copyOf(projects);
    }

    Optional<StatusFieldResult> parseStatusField(JsonNode resposta) {
        if (resposta == null || resposta.isNull()) {
            return Optional.empty();
        }
        JsonNode project = resposta.path("data").path("node");
        if (project.isMissingNode() || project.isNull()) {
            return Optional.empty();
        }
        String typename = texto(project, "__typename");
        if (!"ProjectV2".equals(typename)) {
            return Optional.empty();
        }
        GithubProjectV2ResumoResponse resumo = new GithubProjectV2ResumoResponse(
                texto(project, "id"),
                project.has("number") && project.get("number").isNumber() ? project.get("number").asInt() : null,
                texto(project, "title"),
                texto(project, "url"));

        JsonNode fields = project.path("fields").path("nodes");
        if (!fields.isArray()) {
            return Optional.empty();
        }

        JsonNode statusField = null;
        for (JsonNode field : fields) {
            if (field == null || field.isNull()) {
                continue;
            }
            if (!"ProjectV2SingleSelectField".equals(texto(field, "__typename"))) {
                continue;
            }
            String name = texto(field, "name");
            if (name != null && "status".equalsIgnoreCase(name.trim())) {
                statusField = field;
                break;
            }
            if (statusField == null) {
                statusField = field;
            }
        }
        if (statusField == null) {
            return Optional.empty();
        }

        List<GithubProjectV2StatusOpcaoResponse> opcoes = new ArrayList<>();
        JsonNode options = statusField.path("options");
        if (options.isArray()) {
            for (JsonNode opt : options) {
                if (opt == null || opt.isNull()) {
                    continue;
                }
                String optionId = texto(opt, "id");
                String optionName = texto(opt, "name");
                if (!StringUtils.hasText(optionName)) {
                    continue;
                }
                opcoes.add(new GithubProjectV2StatusOpcaoResponse(
                        optionId,
                        optionName,
                        texto(opt, "color"),
                        texto(opt, "description")));
            }
        }
        return Optional.of(new StatusFieldResult(resumo, List.copyOf(opcoes)));
    }

    private JsonNode executarGraphql(GithubIntegracaoSettings settings, String token, Map<String, Object> body)
            throws Exception {
        var httpFactory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        httpFactory.setConnectTimeout(Duration.ofMillis(settings.httpConnectTimeoutMs()));
        httpFactory.setReadTimeout(Duration.ofMillis(settings.httpReadTimeoutMs()));
        RestClient client = restClientBuilder
                .baseUrl(settings.graphqlUrl())
                .requestFactory(httpFactory)
                .build();

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

    private static List<String> extrairErrosGraphql(JsonNode resposta) {
        if (resposta == null || resposta.isNull()) {
            return List.of();
        }
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

    public record CatalogResult<T>(boolean sucesso, String mensagem, List<String> errosGraphql, T dados) {
        public static <T> CatalogResult<T> falha(String mensagem) {
            return new CatalogResult<>(false, mensagem, List.of(), null);
        }
    }

    public record StatusFieldResult(GithubProjectV2ResumoResponse project, List<GithubProjectV2StatusOpcaoResponse> statusOpcoes) {
    }
}
