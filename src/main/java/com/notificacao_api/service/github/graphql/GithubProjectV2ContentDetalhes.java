package com.notificacao_api.service.github.graphql;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.util.StringUtils;

public record GithubProjectV2ContentDetalhes(
        String titulo,
        String url,
        String contentTypename,
        Integer numero,
        List<GithubGraphqlAssignee> assignees) {

    public GithubProjectV2ContentDetalhes {
        assignees = assignees != null ? List.copyOf(assignees) : List.of();
    }

    public List<String> assigneeLogins() {
        List<String> logins = new ArrayList<>();
        for (GithubGraphqlAssignee assignee : assignees) {
            if (assignee == null || !StringUtils.hasText(assignee.login())) {
                continue;
            }
            String normalizado = assignee.login().trim().toLowerCase(Locale.ROOT);
            if (!logins.contains(normalizado)) {
                logins.add(normalizado);
            }
        }
        return List.copyOf(logins);
    }
}
