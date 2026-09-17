package com.notificacao_api.service.github.graphql;

import java.util.List;

public record GithubGraphqlConsultaResult(
        boolean sucesso,
        String mensagemFalha,
        List<String> errosGraphql,
        GithubProjectV2ContentDetalhes detalhes) {

    public static GithubGraphqlConsultaResult falha(String mensagem) {
        return new GithubGraphqlConsultaResult(false, mensagem, List.of(), null);
    }
}
