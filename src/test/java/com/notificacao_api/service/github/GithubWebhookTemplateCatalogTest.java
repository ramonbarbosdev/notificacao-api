package com.notificacao_api.service.github;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GithubWebhookTemplateCatalogTest {

    @Test
    void catalogoContemVariaveisDocumentadas() {
        assertEquals(18, GithubWebhookTemplateCatalog.VARIAVEIS.size());
        assertEquals(18, GithubWebhookTemplateCatalog.chavesVariaveis().size());
        assertEquals(GithubWebhookWhatsappTemplateService.VARIAVEIS_DISPONIVEIS, GithubWebhookTemplateCatalog.chavesVariaveis());
    }

    @Test
    void cenariosPreviewNaoVazios() {
        assertEquals(6, GithubWebhookTemplateCatalog.CENARIOS_PREVIEW.size());
    }

    @Test
    void mapeiaWebhookParaCenario() {
        assertEquals(
                "projects_v2_edited",
                GithubWebhookTemplateCatalog.cenarioIdPorWebhook("projects_v2_item", "edited").orElseThrow());
        assertTrue(GithubWebhookTemplateCatalog.cenarioIdPorWebhook("issues", "assigned").isEmpty());
    }
}
