package com.notificacao_api.service.github;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.notificacao_api.model.OrganizacaoConfiguracao;

class GithubWebhookWhatsappTemplateServiceTest {

    private final GithubWebhookWhatsappTemplateService service = new GithubWebhookWhatsappTemplateService();

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
                List.of("dev1"));

        var msg = service.formatar(config, "projects_v2_item", "abc-123", dados);

        assertEquals("🔔 Bug login", msg.assunto());
        assertTrue(msg.mensagem().contains("*Em Andamento*"));
        assertTrue(msg.mensagem().contains("https://github.com/o/r/issues/1"));
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
                List.of("octocat"));

        var msg = service.formatar(config, "projects_v2_item", null, dados);

        assertEquals("GitHub: Tarefa X", msg.assunto());
        assertTrue(msg.mensagem().contains("Titulo: Tarefa X"));
        assertTrue(msg.mensagem().contains("Status/Coluna: Review"));
    }
}
