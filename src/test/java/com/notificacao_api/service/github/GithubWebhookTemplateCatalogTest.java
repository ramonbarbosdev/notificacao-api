package com.notificacao_api.service.github;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
        assertEquals(5, GithubWebhookTemplateCatalog.CENARIOS_PREVIEW.size());
    }
}
