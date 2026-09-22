package com.notificacao_api.service.github;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.notificacao_api.model.github.GithubOrganizacaoConfig;

class GithubRegrasPorStatusServiceTest {

    private final GithubRegrasPorStatusService service = new GithubRegrasPorStatusService(new ObjectMapper());

    @Test
    void sincronizaListasLegadasAoAplicarJson() throws Exception {
        GithubOrganizacaoConfig config = new GithubOrganizacaoConfig();
        String json = """
                {
                  "versao": 1,
                  "colunas": {
                    "opt-dev": {
                      "nome": "Validação Interna (Develop)",
                      "aoEntrar": {
                        "fluxoGeral": false,
                        "prAvaliadores": false,
                        "issueAvaliadores": true
                      },
                      "destinatarios": { "modo": "INHERIT" },
                      "mensagem": { "usarTemplatePadrao": true }
                    }
                  }
                }
                """;
        service.aplicarJson(config, json);

        assertEquals("Validação Interna (Develop)", config.getDsGithubIssueStatusDisparo());
        assertTrue(config.getDsGithubStatusDisparo() == null || config.getDsGithubStatusDisparo().isEmpty());
    }

    @Test
    void resolveColunaPorNome() throws Exception {
        GithubOrganizacaoConfig config = new GithubOrganizacaoConfig();
        service.aplicarJson(
                config,
                """
                {"versao":1,"colunas":{"id1":{"nome":"A Fazer","aoEntrar":{"fluxoGeral":true,"prAvaliadores":false,"issueAvaliadores":false},"destinatarios":{"modo":"INHERIT"},"mensagem":{"usarTemplatePadrao":true}}}}
                """);

        var resolvida = service.resolverPorStatusDestino(config, "A Fazer");
        assertTrue(resolvida.isPresent());
        assertTrue(resolvida.get().fluxoGeral());
        assertFalse(resolvida.get().prAvaliadores());
    }

    @Test
    void textoTemplateColunaQuandoTextoProprio() {
        var coluna = new GithubRegrasPorStatusService.GithubRegraColunaJson();
        coluna.mensagem = new GithubRegrasPorStatusService.MensagemJson();
        coluna.mensagem.usarTemplatePadrao = false;
        coluna.mensagem.textoProprioColuna = true;
        coluna.mensagem.assuntoColuna = "Concluído: {{titulo}}";
        coluna.mensagem.mensagemColuna = "Card finalizado em {{status}}";

        var texto = service.textoTemplateColuna(coluna);
        assertTrue(texto.isPresent());
        assertEquals("Concluído: {{titulo}}", texto.get().assunto());
        assertEquals("Card finalizado em {{status}}", texto.get().mensagem());
        assertTrue(service.cenarioTemplateColuna(coluna) == null);
    }
}
