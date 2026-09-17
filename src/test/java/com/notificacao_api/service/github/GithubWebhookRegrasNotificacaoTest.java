package com.notificacao_api.service.github;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.notificacao_api.model.OrganizacaoConfiguracao;
import com.notificacao_api.service.github.GithubWebhookRegrasNotificacao.Gatilho;

class GithubWebhookRegrasNotificacaoTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void statusAlteradoPadraoPermiteProjectV2Edited() throws Exception {
        OrganizacaoConfiguracao config = new OrganizacaoConfiguracao();
        var root = objectMapper.readTree("{\"changes\":{\"field_value\":{\"to\":{\"name\":\"X\"}}}}");
        Set<Gatilho> gatilhos =
                GithubWebhookRegrasNotificacao.classificarGatilhos(config, "projects_v2_item", "edited", root);
        assertTrue(GithubWebhookRegrasNotificacao.deveNotificarPorGatilho(config, gatilhos));
    }

    @Test
    void tarefaCriadaDesligadaIgnoraIssueOpened() {
        OrganizacaoConfiguracao config = new OrganizacaoConfiguracao();
        config.setGithubNotificarTarefaCriada(false);
        Set<Gatilho> gatilhos =
                GithubWebhookRegrasNotificacao.classificarGatilhos(config, "issues", "opened", null);
        assertFalse(GithubWebhookRegrasNotificacao.deveNotificarPorGatilho(config, gatilhos));
    }

    @Test
    void naoNotificarMovimentadorRemoveSender() {
        OrganizacaoConfiguracao config = new OrganizacaoConfiguracao();
        config.setGithubNaoNotificarMovimentador(true);
        config.setDsGithubDestinatariosModo("RESPONSAVEIS_E_MOVIMENTADOR");

        List<String> logins = GithubWebhookRegrasNotificacao.resolverLoginsDestino(
                config, "projects_v2_item", null, List.of("dev1"), "actor");

        assertEquals(List.of("dev1"), logins);
    }

    @Test
    void movimentadorQueEAssigneeContinuaDestino() {
        OrganizacaoConfiguracao config = new OrganizacaoConfiguracao();
        config.setGithubNaoNotificarMovimentador(true);

        List<String> logins = GithubWebhookRegrasNotificacao.resolverLoginsDestino(
                config, "projects_v2_item", null, List.of("dev1"), "dev1");

        assertEquals(List.of("dev1"), logins);
    }

    @Test
    void ignorarSemResponsavelNaoUsaSender() {
        OrganizacaoConfiguracao config = new OrganizacaoConfiguracao();
        config.setGithubIgnorarSemResponsavel(true);

        List<String> logins = GithubWebhookRegrasNotificacao.resolverLoginsDestino(
                config, "projects_v2_item", null, List.of(), "sender");

        assertTrue(logins.isEmpty());
    }

    @Test
    void loginsConfiguradosUsaExtras() {
        OrganizacaoConfiguracao config = new OrganizacaoConfiguracao();
        config.setDsGithubDestinatariosModo("LOGINS_CONFIGURADOS");
        config.setDsGithubDestinatariosExtras("lead, devops");

        List<String> logins = GithubWebhookRegrasNotificacao.resolverLoginsDestino(
                config, "issues", null, List.of("assignee"), "sender");

        assertEquals(List.of("lead", "devops"), logins);
    }
}
