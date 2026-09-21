package com.notificacao_api.service.github.graphql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.notificacao_api.dto.integracao.GithubProjectV2ResumoResponse;
import com.notificacao_api.dto.integracao.GithubProjectV2StatusOpcaoResponse;

class GithubProjectV2CatalogServiceTest {

    private GithubProjectV2CatalogService service;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        service = new GithubProjectV2CatalogService(
                org.springframework.web.client.RestClient.builder(), objectMapper);
    }

    @Test
    void parseProjectsExtraiLista() throws Exception {
        String json = """
                {
                  "data": {
                    "organization": {
                      "projectsV2": {
                        "nodes": [
                          {
                            "id": "PVT_kwDOEKnzAs4BWPN4",
                            "number": 4,
                            "title": "Board Principal",
                            "url": "https://github.com/orgs/gpi-organizacao/projects/4"
                          }
                        ]
                      }
                    }
                  }
                }
                """;
        var projects = service.parseProjects(objectMapper.readTree(json));
        assertEquals(1, projects.size());
        assertEquals("PVT_kwDOEKnzAs4BWPN4", projects.get(0).id());
        assertEquals(4, projects.get(0).number());
        assertEquals("Board Principal", projects.get(0).title());
    }

    @Test
    void parseStatusFieldExtraiOpcoesDoCampoStatus() throws Exception {
        String json = """
                {
                  "data": {
                    "node": {
                      "__typename": "ProjectV2",
                      "id": "PVT_kwDOEKnzAs4BWPN4",
                      "number": 4,
                      "title": "Board",
                      "url": "https://github.com/orgs/x/projects/4",
                      "fields": {
                        "nodes": [
                          {
                            "__typename": "ProjectV2SingleSelectField",
                            "id": "field1",
                            "name": "Status",
                            "options": [
                              {
                                "id": "df73e18b",
                                "name": "Validação Interna (Develop)",
                                "color": "PURPLE",
                                "description": "Validação interna"
                              },
                              {
                                "id": "61e4505c",
                                "name": "A Fazer",
                                "color": "BLUE",
                                "description": null
                              }
                            ]
                          }
                        ]
                      }
                    }
                  }
                }
                """;
        var parsed = service.parseStatusField(objectMapper.readTree(json));
        assertTrue(parsed.isPresent());
        GithubProjectV2ResumoResponse project = parsed.get().project();
        assertEquals("PVT_kwDOEKnzAs4BWPN4", project.id());
        assertEquals(2, parsed.get().statusOpcoes().size());
        GithubProjectV2StatusOpcaoResponse primeira = parsed.get().statusOpcoes().get(0);
        assertEquals("Validação Interna (Develop)", primeira.name());
        assertEquals("df73e18b", primeira.optionId());
    }
}
