package com.notificacao_api.service.github.graphql;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

class GithubProjectV2IntegracaoServiceTest {

    @Test
    void parseListaDisparoSeparaPorVirgula() {
        assertEquals(List.of(), GithubProjectV2IntegracaoService.parseListaDisparo(null));
        assertEquals(List.of(), GithubProjectV2IntegracaoService.parseListaDisparo("  "));
        assertEquals(
                List.of("Validação Interna (Develop)", "A Fazer"),
                GithubProjectV2IntegracaoService.parseListaDisparo("Validação Interna (Develop), A Fazer"));
    }
}
