package com.notificacao_api.dto.integracao;

import java.util.List;

public record GithubGraphqlConsultaResponse(
        Long idOrganizacao,
        String graphqlUrl,
        boolean tokenDisponivel,
        boolean githubAppCredenciaisOk,
        Long githubInstallationId,
        boolean githubPatConfigurado,
        String nodeId,
        String contentType,
        boolean sucesso,
        String mensagemFalha,
        List<String> errosGraphql,
        String contentTypename,
        String titulo,
        String url,
        Integer numero,
        List<GithubGraphqlAssigneeConsultaResponse> assignees) {

    public record GithubGraphqlAssigneeConsultaResponse(String login, String name) {
    }
}
