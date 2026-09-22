package com.notificacao_api.service.github;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.notificacao_api.model.github.GithubOrganizacaoConfig;
import com.notificacao_api.service.github.GithubWebhookRegrasNotificacao.Gatilho;

class GithubWebhookRegrasNotificacaoTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void statusAlteradoPadraoPermiteProjectV2Edited() throws Exception {
        GithubOrganizacaoConfig config = new GithubOrganizacaoConfig();
        var root = objectMapper.readTree("{\"changes\":{\"field_value\":{\"to\":{\"name\":\"X\"}}}}");
        Set<Gatilho> gatilhos =
                GithubWebhookRegrasNotificacao.classificarGatilhos(config, "projects_v2_item", "edited", root);
        assertTrue(GithubWebhookRegrasNotificacao.deveNotificarPorGatilho(config, gatilhos));
    }

    @Test
    void reordenacaoDesligadaIgnoraProjectV2Reordered() {
        GithubOrganizacaoConfig config = new GithubOrganizacaoConfig();
        config.setGithubNotificarReordenacao(false);
        config.setGithubNotificarStatusAlterado(true);
        Set<Gatilho> gatilhos =
                GithubWebhookRegrasNotificacao.classificarGatilhos(config, "projects_v2_item", "reordered", null);
        assertEquals(Set.of(Gatilho.REORDENADO), gatilhos);
        assertFalse(GithubWebhookRegrasNotificacao.deveNotificarPorGatilho(config, gatilhos));
    }

    @Test
    void reordenacaoLigadaPermiteProjectV2Reordered() {
        GithubOrganizacaoConfig config = new GithubOrganizacaoConfig();
        config.setGithubNotificarReordenacao(true);
        Set<Gatilho> gatilhos =
                GithubWebhookRegrasNotificacao.classificarGatilhos(config, "projects_v2_item", "reordered", null);
        assertTrue(GithubWebhookRegrasNotificacao.deveNotificarPorGatilho(config, gatilhos));
    }

    @Test
    void filtroStatusColunaGeralConfiguravelPorGatilho() {
        GithubOrganizacaoConfig config = new GithubOrganizacaoConfig();
        config.setGithubNotificarResponsavelAlterado(true);
        config.setGithubNotificarStatusAlterado(true);
        config.setGithubNotificarReordenacao(true);
        config.setDsGithubStatusDisparoGatilhos("STATUS_ALTERADO,REORDENADO");

        assertFalse(GithubWebhookRegrasNotificacao.deveAplicarFiltroStatusColunaGeral(
                config, Set.of(Gatilho.RESPONSAVEL_ALTERADO, Gatilho.TAREFA_ATRIBUIDA)));
        assertTrue(GithubWebhookRegrasNotificacao.deveAplicarFiltroStatusColunaGeral(
                config, Set.of(Gatilho.STATUS_ALTERADO)));
        assertTrue(GithubWebhookRegrasNotificacao.deveAplicarFiltroStatusColunaGeral(
                config, Set.of(Gatilho.REORDENADO)));

        config.setGithubNotificarTarefaAtribuida(false);
        config.setDsGithubStatusDisparoGatilhos("RESPONSAVEL_ALTERADO,TAREFA_ATRIBUIDA");
        assertTrue(GithubWebhookRegrasNotificacao.deveAplicarFiltroStatusColunaGeral(
                config, Set.of(Gatilho.RESPONSAVEL_ALTERADO)));
        assertFalse(GithubWebhookRegrasNotificacao.deveAplicarFiltroStatusColunaGeral(
                config, Set.of(Gatilho.STATUS_ALTERADO)));
    }

    @Test
    void tarefaCriadaDesligadaIgnoraIssueOpened() {
        GithubOrganizacaoConfig config = new GithubOrganizacaoConfig();
        config.setGithubNotificarTarefaCriada(false);
        Set<Gatilho> gatilhos =
                GithubWebhookRegrasNotificacao.classificarGatilhos(config, "issues", "opened", null);
        assertFalse(GithubWebhookRegrasNotificacao.deveNotificarPorGatilho(config, gatilhos));
    }

    @Test
    void naoNotificarMovimentadorRemoveSender() {
        GithubOrganizacaoConfig config = new GithubOrganizacaoConfig();
        config.setGithubNaoNotificarMovimentador(true);
        config.setDsGithubDestinatariosModo("RESPONSAVEIS_E_MOVIMENTADOR");

        List<String> logins = GithubWebhookRegrasNotificacao.resolverLoginsDestino(
                config, "projects_v2_item", null, List.of("dev1"), "actor");

        assertEquals(List.of("dev1"), logins);
    }

    @Test
    void movimentadorQueEAssigneeContinuaDestino() {
        GithubOrganizacaoConfig config = new GithubOrganizacaoConfig();
        config.setGithubNaoNotificarMovimentador(true);

        List<String> logins = GithubWebhookRegrasNotificacao.resolverLoginsDestino(
                config, "projects_v2_item", null, List.of("dev1"), "dev1");

        assertEquals(List.of("dev1"), logins);
    }

    @Test
    void ignorarSemResponsavelNaoUsaSender() {
        GithubOrganizacaoConfig config = new GithubOrganizacaoConfig();
        config.setGithubIgnorarSemResponsavel(true);

        List<String> logins = GithubWebhookRegrasNotificacao.resolverLoginsDestino(
                config, "projects_v2_item", null, List.of(), "sender");

        assertTrue(logins.isEmpty());
    }

    @Test
    void loginsConfiguradosUsaExtras() {
        GithubOrganizacaoConfig config = new GithubOrganizacaoConfig();
        config.setDsGithubDestinatariosModo("LOGINS_CONFIGURADOS");
        config.setDsGithubDestinatariosExtras("lead, devops");

        List<String> logins = GithubWebhookRegrasNotificacao.resolverLoginsDestino(
                config, "issues", null, List.of("assignee"), "sender");

        assertEquals(List.of("lead", "devops"), logins);
    }
}
