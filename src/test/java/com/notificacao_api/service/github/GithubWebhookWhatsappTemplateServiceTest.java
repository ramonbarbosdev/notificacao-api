package com.notificacao_api.service.github;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.notificacao_api.dto.configuracao.GithubTemplatePorCenarioDto;
import com.notificacao_api.model.github.GithubOrganizacaoConfig;

class GithubWebhookWhatsappTemplateServiceTest {

    private final GithubWebhookWhatsappTemplateService service = new GithubWebhookWhatsappTemplateService(
            new GithubWebhookTemplatesPorCenarioService(new ObjectMapper()));

    @Test
    void nomeDestinatarioSubstituidoNoTemplate() {
        GithubOrganizacaoConfig config = new GithubOrganizacaoConfig();
        config.setDsGithubTemplateMensagemWhatsapp("Oi {{nome_destinatario}}, card {{titulo}}");

        var dados = new GithubWebhookWhatsappTemplateService.GithubWebhookEventoDados(
                "Bug login",
                "Em Andamento",
                "A Fazer",
                "ctx",
                "edited",
                null,
                "dev1",
                List.of("dev1"),
                List.of("dev1"),
                "STATUS_ALTERADO",
                null);
        var destinatario = new GithubWhatsappDestinatario("5571999888777", "maria.dev", "Maria");

        var msg = service.formatar(config, "projects_v2_item", null, dados, null, null, destinatario);

        assertTrue(msg.mensagem().contains("Oi Maria, card Bug login"));
    }

    @Test
    void responsaveisUsaAssigneesEDestinatariosSeparados() {
        GithubOrganizacaoConfig config = new GithubOrganizacaoConfig();
        config.setDsGithubTemplateMensagemWhatsapp(
                "Resp: {{responsaveis}} Dest: {{destinatarios}} Mov: {{movimentador}}");

        var dados = new GithubWebhookWhatsappTemplateService.GithubWebhookEventoDados(
                "T",
                "Em Andamento",
                "A Fazer",
                "ctx",
                "edited",
                null,
                "quem-moveu",
                List.of("joao"),
                List.of("joao", "maria"),
                "STATUS_ALTERADO",
                null);

        var msg = service.formatar(config, "projects_v2_item", null, dados);

        assertTrue(msg.mensagem().contains("Resp: @joao"));
        assertTrue(msg.mensagem().contains("Dest: joao, maria"));
        assertTrue(msg.mensagem().contains("Mov: quem-moveu"));
    }

    @Test
    void templateCustomizadoSubstituiVariaveis() {
        GithubOrganizacaoConfig config = new GithubOrganizacaoConfig();
        config.setDsGithubTemplateAssuntoWhatsapp("🔔 {{titulo}}");
        config.setDsGithubTemplateMensagemWhatsapp("*{{status}}* — {{acao}}\n{{url}}");

        var dados = new GithubWebhookWhatsappTemplateService.GithubWebhookEventoDados(
                "Bug login",
                "Em Andamento",
                "ctx",
                "edited",
                "https://github.com/o/r/issues/1",
                "dev1",
                List.of("dev1"),
                1);

        var msg = service.formatar(config, "projects_v2_item", "abc-123", dados);

        assertEquals("🔔 Bug login", msg.assunto());
        assertTrue(msg.mensagem().contains("*Em Andamento*"));
        assertTrue(msg.mensagem().contains("https://github.com/o/r/issues/1"));
    }

    @Test
    void textoWhatsappJuntaAssuntoEMensagem() {
        GithubOrganizacaoConfig config = new GithubOrganizacaoConfig();
        config.setDsGithubTemplateAssuntoWhatsapp("Titulo {{titulo}}");
        config.setDsGithubTemplateMensagemWhatsapp("Corpo {{status}}");

        var dados = new GithubWebhookWhatsappTemplateService.GithubWebhookEventoDados(
                "X", "Y", "ctx", "edited", null, null, List.of(), null);

        var msg = service.formatar(config, "issues", null, dados);

        assertEquals("Titulo X\n\nCorpo Y", msg.textoWhatsapp());
    }

    @Test
    void templatePadraoQuandoConfigVazia() {
        GithubOrganizacaoConfig config = new GithubOrganizacaoConfig();
        var dados = new GithubWebhookWhatsappTemplateService.GithubWebhookEventoDados(
                "Tarefa X",
                "Review",
                "Item atualizado no Project (v2)",
                "edited",
                null,
                "octocat",
                List.of("octocat"),
                null);

        var msg = service.formatar(config, "projects_v2_item", null, dados);

        assertEquals("GitHub: Tarefa X", msg.assunto());
        assertTrue(msg.mensagem().contains("Titulo: Tarefa X"));
        assertTrue(msg.mensagem().contains("Status/Coluna: Review"));
    }

    @Test
    void previewPorCenarioSubstituiTitulo() {
        var preview = service.preview(
                "GitHub: {{titulo}}",
                "Status: {{status}}",
                "projects_v2_edited");

        assertEquals("GitHub: Implementar funcionalidade X", preview.assunto());
        assertTrue(preview.mensagem().contains("Status: Em Andamento"));
        assertTrue(preview.textoWhatsapp().contains("Implementar funcionalidade X"));
        assertTrue(preview.variaveisDesconhecidas().isEmpty());
        assertEquals("STATUS_ALTERADO", preview.contextoEvento().get("evento"));
        assertEquals("A Fazer", preview.contextoEvento().get("status_anterior"));
    }

    @Test
    void templatePadraoPrAvaliadoresDiferenteDeStatusAlterado() {
        GithubOrganizacaoConfig config = new GithubOrganizacaoConfig();
        var dados = new GithubWebhookWhatsappTemplateService.GithubWebhookEventoDados(
                "feat: login",
                "Em revisão",
                "Em andamento",
                "Pull Request atualizado no Project (v2)",
                "edited",
                "https://github.com/org/repo/pull/99",
                "author",
                List.of(),
                List.of("reviewer1"),
                "PR_AVALIADORES",
                99);

        var msg = service.formatar(
                config,
                "projects_v2_item",
                null,
                dados,
                GithubWebhookTemplateCatalog.CENARIO_PR_AVALIADORES);

        assertTrue(msg.assunto().contains("PR para revisão"));
        assertTrue(msg.mensagem().contains("Revisão solicitada"));
        assertTrue(msg.mensagem().contains("reviewer1"));
        assertFalse(msg.mensagem().contains("Status/Coluna:"));
    }

    @Test
    void usaTemplatePorCenarioPrQuandoConfigurado() throws Exception {
        GithubOrganizacaoConfig config = new GithubOrganizacaoConfig();
        new GithubWebhookTemplatesPorCenarioService(new ObjectMapper()).aplicar(
                config,
                java.util.Map.of(
                        GithubWebhookTemplateCatalog.CENARIO_PR_AVALIADORES,
                        new GithubTemplatePorCenarioDto("🔔 PR {{titulo}}", "Olá {{destinatarios}} — {{status}}")));

        var dados = new GithubWebhookWhatsappTemplateService.GithubWebhookEventoDados(
                "Minha PR",
                "Validação",
                null,
                "ctx",
                "edited",
                null,
                "dev",
                List.of(),
                List.of("lead"),
                "PR_AVALIADORES",
                5);

        var msg = service.formatar(
                config,
                "projects_v2_item",
                null,
                dados,
                GithubWebhookTemplateCatalog.CENARIO_PR_AVALIADORES);

        assertEquals("🔔 PR Minha PR", msg.assunto());
        assertTrue(msg.mensagem().contains("Olá lead"));
    }

    @Test
    void previewPorCenarioLegadoPrAvaliadoresUsaExemploStatusAlterado() {
        var preview = service.preview(
                "Assunto {{titulo}}",
                "Para {{destinatarios}}",
                GithubWebhookTemplateCatalog.CENARIO_PR_AVALIADORES);

        assertEquals("STATUS_ALTERADO", preview.contextoEvento().get("evento"));
        assertTrue(preview.mensagem().contains("joao"));
    }

    @Test
    void usaTemplatePorCenarioQuandoConfigurado() throws Exception {
        GithubOrganizacaoConfig config = new GithubOrganizacaoConfig();
        new GithubWebhookTemplatesPorCenarioService(new ObjectMapper()).aplicar(
                config,
                java.util.Map.of(
                        "projects_v2_edited",
                        new GithubTemplatePorCenarioDto("Assunto v2", "Corpo exclusivo v2 {{titulo}}")));

        var dados = new GithubWebhookWhatsappTemplateService.GithubWebhookEventoDados(
                "T",
                "S",
                null,
                "ctx",
                "edited",
                null,
                null,
                List.of(),
                List.of(),
                "STATUS_ALTERADO",
                null);

        var msg = service.formatar(config, "projects_v2_item", null, dados);

        assertTrue(msg.mensagem().contains("Corpo exclusivo v2"));
        assertEquals("Assunto v2", msg.assunto());
    }

    @Test
    void usaTextoProprioDaColunaQuandoInformado() {
        GithubOrganizacaoConfig config = new GithubOrganizacaoConfig();
        var dados = new GithubWebhookWhatsappTemplateService.GithubWebhookEventoDados(
                "Card X",
                "Concluído",
                "Em revisão",
                "ctx",
                "edited",
                "https://github.com/o/r/issues/1",
                "dev",
                List.of(),
                List.of("dev"),
                "STATUS_ALTERADO",
                1);
        var textoColuna = new GithubRegrasPorStatusService.TextoTemplateColuna(
                "Fechado: {{titulo}}", "Saiu de {{status_anterior}} para {{status}}");

        var msg = service.formatar(config, "projects_v2_item", null, dados, null, textoColuna);

        assertEquals("Fechado: Card X", msg.assunto());
        assertTrue(msg.mensagem().contains("Saiu de Em revisão para Concluído"));
    }

    @Test
    void variaveisDesconhecidasDetectadas() {
        var desconhecidas = service.variaveisDesconhecidas("{{titulo}}", "Numero: {{issue_number}}");

        assertTrue(desconhecidas.contains("issue_number"));
        assertFalse(desconhecidas.contains("titulo"));
    }
}
