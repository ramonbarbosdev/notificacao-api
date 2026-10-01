package com.notificacao_api.service.github.graphql;

import java.util.LinkedHashMap;
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
import com.notificacao_api.model.github.OrganizacaoGithubIntegracao;
import com.notificacao_api.service.OrganizacaoGithubIntegracaoSettingsService;
import com.notificacao_api.service.github.GithubIntegracaoSettings;

@Service
public class GithubGraphqlLoginNameResolver {

    private static final Logger log = LoggerFactory.getLogger(GithubGraphqlLoginNameResolver.class);

    private static final String QUERY = """
            query($login: String!) {
              user(login: $login) {
                login
                name
              }
            }
            """;

    private final OrganizacaoGithubIntegracaoSettingsService integracaoSettingsService;
    private final GithubGraphqlAccessTokenResolver accessTokenResolver;
    private final RestClient.Builder restClientBuilder;
    private final ObjectMapper objectMapper;

    public GithubGraphqlLoginNameResolver(
            OrganizacaoGithubIntegracaoSettingsService integracaoSettingsService,
            GithubGraphqlAccessTokenResolver accessTokenResolver,
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper) {
        this.integracaoSettingsService = integracaoSettingsService;
        this.accessTokenResolver = accessTokenResolver;
        this.restClientBuilder = restClientBuilder;
        this.objectMapper = objectMapper;
    }

    public Optional<String> resolverNome(Long idOrganizacao, OrganizacaoGithubIntegracao integracao, String login) {
        if (!StringUtils.hasText(login) || integracao == null) {
            return Optional.empty();
        }
        GithubIntegracaoSettings settings = integracaoSettingsService.resolver(integracao);
        Long installationId = integracao.getNuGithubInstallationId();
        String token = accessTokenResolver
                .resolverBearer(idOrganizacao, integracao, settings, installationId)
                .orElse(null);
        if (!StringUtils.hasText(token) || settings == null || !StringUtils.hasText(settings.graphqlUrl())) {
            return Optional.empty();
        }

        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("query", QUERY);
            body.put("variables", Map.of("login", login.trim()));

            RestClient client = restClientBuilder
                    .baseUrl(settings.graphqlUrl())
                    .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .build();

            String resposta = client.post()
                    .body(objectMapper.writeValueAsString(body))
                    .retrieve()
                    .body(String.class);

            if (!StringUtils.hasText(resposta)) {
                return Optional.empty();
            }
            JsonNode root = objectMapper.readTree(resposta);
            JsonNode user = root.path("data").path("user");
            if (user.isMissingNode() || user.isNull()) {
                return Optional.empty();
            }
            String nome = user.path("name").asText(null);
            return StringUtils.hasText(nome) ? Optional.of(nome.trim()) : Optional.empty();
        } catch (Exception ex) {
            log.debug(
                    "GraphQL nome login falhou org={} login={} motivo={}",
                    idOrganizacao,
                    login,
                    ex.getMessage());
            return Optional.empty();
        }
    }
}
