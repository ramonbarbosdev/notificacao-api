package com.notificacao_api.service.github;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.notificacao_api.dto.integracao.GithubKanbanMovimentacaoWebhookPayload;
import com.notificacao_api.dto.integracao.GithubKanbanWebhookPessoa;
import com.notificacao_api.enums.GithubKanbanWebhookModoEnvio;
import com.notificacao_api.service.github.OrganizacaoGithubKanbanMovimentacaoWebhookService.ConfiguracaoKanbanMovimentacaoWebhook;

@ExtendWith(MockitoExtension.class)
class GithubKanbanMovimentacaoWebhookServiceTest {

    @Mock
    private OrganizacaoGithubKanbanMovimentacaoWebhookService configService;

    @Mock
    private GithubKanbanWebhookFilaService filaService;

    @Mock
    private GithubKanbanWebhookNomeEnriquecimentoService nomeEnriquecimentoService;

    private GithubKanbanMovimentacaoWebhookService service;

    @BeforeEach
    void setUp() {
        service = new GithubKanbanMovimentacaoWebhookService(
                configService, filaService, nomeEnriquecimentoService, org.springframework.web.client.RestClient.builder());
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
    void ehMovimentacaoKanbanParaWebhookExterno_reorderedIgnorado() throws Exception {
        var dados = new GithubWebhookService.MensagemKanban(
                "Titulo",
                "Review",
                "Todo",
                "ctx",
                "reordered",
                null,
                "dev",
                List.of(),
                false,
                true,
                10);
        assertFalse(GithubWebhookService.ehMovimentacaoKanbanParaWebhookExterno("projects_v2_item", dados, null));
    }

    @Test
    void ehMovimentacaoKanbanParaWebhookExterno_mesmaColunaIgnorado() throws Exception {
        var root = new ObjectMapper().readTree("""
                {"action":"edited","changes":{"field_value":{"to":{"name":"Review"}}}}
                """);
        var dados = new GithubWebhookService.MensagemKanban(
                "Titulo",
                "Review",
                "Review",
                "ctx",
                "edited",
                null,
                "dev",
                List.of(),
                false,
                true,
                10);
        assertFalse(GithubWebhookService.ehMovimentacaoKanbanParaWebhookExterno("projects_v2_item", dados, root));
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

        when(nomeEnriquecimentoService.movidoPor(eq(1L), any(), eq("mover"), anyMap()))
                .thenReturn(new GithubKanbanWebhookPessoa("mover", "Mover"));
        when(nomeEnriquecimentoService.responsaveis(eq(1L), any(), anyList(), anyMap(), eq(null)))
                .thenReturn(List.of(new GithubKanbanWebhookPessoa("octocat", "Octocat")));

        GithubKanbanMovimentacaoWebhookPayload payload =
                service.montarPayload(1L, root, dados, List.of("octocat"), new HashMap<>());

        assertEquals("esimples-api", payload.repo());
        assertEquals("issue", payload.tipo());
        assertEquals(42, payload.numero());
        assertEquals("Octocat", payload.responsaveisDetalhe().get(0).nome());
    }

    @Test
    void dispararEventoFicticio_semConfigRetornaFalse() {
        when(configService.resolver(1L))
                .thenReturn(config(false, null, null));
        assertFalse(service.dispararEventoFicticio(1L));
    }

    @Test
    void dispararEventoFicticio_comConfigSemServidorRetornaFalse() {
        when(configService.resolver(1L))
                .thenReturn(config(true, "http://127.0.0.1:9/x", "Bearer x", GithubKanbanWebhookModoEnvio.IMEDIATO));
        assertFalse(service.dispararEventoFicticio(1L));
    }

    private static ConfiguracaoKanbanMovimentacaoWebhook config(
            boolean habilitado, String url, String auth, GithubKanbanWebhookModoEnvio modo) {
        return new ConfiguracaoKanbanMovimentacaoWebhook(habilitado, url, auth, true, modo, 30);
    }

    private static ConfiguracaoKanbanMovimentacaoWebhook config(boolean habilitado, String url, String auth) {
        return config(habilitado, url, auth, GithubKanbanWebhookModoEnvio.IMEDIATO);
    }
}
