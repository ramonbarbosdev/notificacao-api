package com.notificacao_api.service.github.graphql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.notificacao_api.service.github.GithubIntegracaoSettings;

class GithubGraphqlContentResolverTest {

    private GithubGraphqlContentResolver resolver;
    private GithubIntegracaoSettings settings;

    @BeforeEach
    void setUp() {
        resolver = new GithubGraphqlContentResolver(RestClient.builder(), new ObjectMapper());
        settings = new GithubIntegracaoSettings("https://api.github.com/graphql", "https://api.github.com", 1000, 1000, 300);
    }

    @Test
    void parseRespostaIssueRetornaTituloEUrl() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        var body = mapper.readTree("""
                {
                  "data": {
                    "node": {
                      "__typename": "Issue",
                      "title": "Corrigir login",
                      "url": "https://github.com/org/repo/issues/42"
                    }
                  }
                }
                """);

        Optional<GithubProjectV2ContentDetalhes> resultado =
                resolver.parseResposta(1L, "I_xxx", "Issue", body);

        assertTrue(resultado.isPresent());
        assertEquals("Corrigir login", resultado.get().titulo());
        assertEquals("https://github.com/org/repo/issues/42", resultado.get().url());
        assertEquals("Issue", resultado.get().contentTypename());
    }

    @Test
    void parseRespostaIssueComAssigneesENumero() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        var body = mapper.readTree("""
                {
                  "data": {
                    "node": {
                      "__typename": "Issue",
                      "title": "Implementar funcionalidade X",
                      "number": 123,
                      "url": "https://github.com/gpi-organizacao/esimples-api/issues/123",
                      "assignees": {
                        "nodes": [
                          { "login": "joao", "name": "João Silva" },
                          { "login": "maria", "name": "Maria Souza" }
                        ]
                      }
                    }
                  }
                }
                """);

        Optional<GithubProjectV2ContentDetalhes> resultado =
                resolver.parseResposta(1L, "I_xxx", "Issue", body);

        assertTrue(resultado.isPresent());
        assertEquals(123, resultado.get().numero());
        assertEquals(List.of("joao", "maria"), resultado.get().assigneeLogins());
        assertEquals(2, resultado.get().assignees().size());
    }

    @Test
    void parseRespostaComErrorsRetornaVazio() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        var body = mapper.readTree("""
                { "errors": [ { "message": "Not found" } ] }
                """);

        assertTrue(resolver.parseResposta(1L, "I_xxx", "Issue", body).isEmpty());
    }

    @Test
    void enriquecerSemTokenRetornaVazio() {
        assertTrue(resolver.enriquecer(1L, settings, null, "I_xxx", "Issue").isEmpty());
        assertTrue(resolver.enriquecer(1L, settings, "  ", "I_xxx", "Issue").isEmpty());
    }
}
