package com.notificacao_api.service.github;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.notificacao_api.dto.integracao.GithubKanbanMovimentacaoWebhookPayload;
import com.notificacao_api.service.github.OrganizacaoGithubKanbanMovimentacaoWebhookService.ConfiguracaoKanbanMovimentacaoWebhook;

@ExtendWith(MockitoExtension.class)
class GithubKanbanMovimentacaoWebhookServiceTest {

    @Mock
    private OrganizacaoGithubKanbanMovimentacaoWebhookService configService;

    private GithubKanbanMovimentacaoWebhookService service;

    @BeforeEach
    void setUp() {
        service = new GithubKanbanMovimentacaoWebhookService(
                configService, org.springframework.web.client.RestClient.builder());
    }

    @Test
    void ehMovimentacaoKanbanParaWebhookExterno_editedComColuna() throws Exception {
        var root = new ObjectMapper().readTree("""
                {"action":"edited","changes":{"field_value":{"to":{"name":"Review"}}}}
                """);
        var dados = new GithubWebhookService.MensagemKanban(
                "Titulo",
                "Review",
                "Todo",
                "ctx",
                "edited",
                null,
                "dev",
                List.of(),
                false,
                true,
                10);
        assertTrue(GithubWebhookService.ehMovimentacaoKanbanParaWebhookExterno("projects_v2_item", dados, root));
    }

    @Test
    void montarPayload_repoETipoIssue() throws Exception {
        var root = new ObjectMapper().readTree("""
                {
                  "issue": {
                    "number": 42,
                    "title": "Bug",
                    "html_url": "https://github.com/gpi-organizacao/esimples-api/issues/42",
                    "labels": [{"name": "bug"}]
                  }
                }
                """);
        var dados = new GithubWebhookService.MensagemKanban(
                "Bug",
                "Em revisão",
                "Em Andamento",
                "ctx",
                "edited",
                "https://github.com/gpi-organizacao/esimples-api/issues/42",
                "mover",
                List.of("octocat"),
                false,
                true,
                42);

        GithubKanbanMovimentacaoWebhookPayload payload = service.montarPayload(root, dados, List.of("octocat"));

        assertEquals("esimples-api", payload.repo());
        assertEquals("issue", payload.tipo());
        assertEquals(42, payload.numero());
    }

    @Test
    void dispararEventoFicticio_semConfigRetornaFalse() {
        when(configService.resolver(1L))
                .thenReturn(new ConfiguracaoKanbanMovimentacaoWebhook(false, null, null, true));
        assertFalse(service.dispararEventoFicticio(1L));
    }

    @Test
    void dispararEventoFicticio_comConfigRetornaTrue() {
        when(configService.resolver(1L))
                .thenReturn(new ConfiguracaoKanbanMovimentacaoWebhook(true, "http://127.0.0.1:9/x", "Bearer x", true));
        assertTrue(service.dispararEventoFicticio(1L));
    }
}
