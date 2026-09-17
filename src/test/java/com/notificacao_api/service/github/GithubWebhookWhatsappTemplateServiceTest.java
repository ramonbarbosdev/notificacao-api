package com.notificacao_api.service.github;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.notificacao_api.model.OrganizacaoConfiguracao;

class GithubWebhookWhatsappTemplateServiceTest {

    private final GithubWebhookWhatsappTemplateService service = new GithubWebhookWhatsappTemplateService();

    @Test
    void responsaveisUsaAssigneesEDestinatariosSeparados() {
        OrganizacaoConfiguracao config = new OrganizacaoConfiguracao();
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
        OrganizacaoConfiguracao config = new OrganizacaoConfiguracao();
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
        OrganizacaoConfiguracao config = new OrganizacaoConfiguracao();
        config.setDsGithubTemplateAssuntoWhatsapp("Titulo {{titulo}}");
        config.setDsGithubTemplateMensagemWhatsapp("Corpo {{status}}");

        var dados = new GithubWebhookWhatsappTemplateService.GithubWebhookEventoDados(
                "X", "Y", "ctx", "edited", null, null, List.of(), null);

        var msg = service.formatar(config, "issues", null, dados);

        assertEquals("Titulo X\n\nCorpo Y", msg.textoWhatsapp());
    }

    @Test
    void templatePadraoQuandoConfigVazia() {
        OrganizacaoConfiguracao config = new OrganizacaoConfiguracao();
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
    void variaveisDesconhecidasDetectadas() {
        var desconhecidas = service.variaveisDesconhecidas("{{titulo}}", "Numero: {{issue_number}}");

        assertTrue(desconhecidas.contains("issue_number"));
        assertFalse(desconhecidas.contains("titulo"));
    }
}
