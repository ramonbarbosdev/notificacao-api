package com.notificacao_api.service.github;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.notificacao_api.dto.integracao.GithubKanbanWebhookPessoa;
import com.notificacao_api.model.github.OrganizacaoGithubIntegracao;
import com.notificacao_api.service.github.graphql.GithubGraphqlLoginNameResolver;

@Service
public class GithubKanbanWebhookNomeEnriquecimentoService {

    private final OrganizacaoGithubResponsavelService responsavelService;
    private final GithubGraphqlLoginNameResolver graphqlLoginNameResolver;

    public GithubKanbanWebhookNomeEnriquecimentoService(
            OrganizacaoGithubResponsavelService responsavelService,
            GithubGraphqlLoginNameResolver graphqlLoginNameResolver) {
        this.responsavelService = responsavelService;
        this.graphqlLoginNameResolver = graphqlLoginNameResolver;
    }

    public GithubKanbanWebhookPessoa movidoPor(
            Long idOrganizacao, JsonNode root, String senderLogin, Map<String, String> cacheNomes) {
        if (!StringUtils.hasText(senderLogin)) {
            return null;
        }
        String login = senderLogin.trim();
        String nomeWebhook = extrairNomeSender(root);
        String nome = resolverNome(idOrganizacao, login, nomeWebhook, cacheNomes, null);
        return new GithubKanbanWebhookPessoa(login, nome);
    }

    public List<GithubKanbanWebhookPessoa> responsaveis(
            Long idOrganizacao,
            JsonNode root,
            List<String> loginsResponsaveis,
            Map<String, String> cacheNomes,
            OrganizacaoGithubIntegracao integracao) {
        if (loginsResponsaveis == null || loginsResponsaveis.isEmpty()) {
            return null;
        }
        Map<String, String> nomesIssue = extrairNomesAssignees(root);
        List<GithubKanbanWebhookPessoa> lista = new ArrayList<>();
        for (String loginBruto : loginsResponsaveis) {
            if (!StringUtils.hasText(loginBruto)) {
                continue;
            }
            String login = loginBruto.trim();
            String nome = resolverNome(
                    idOrganizacao, login, nomesIssue.get(login.toLowerCase(Locale.ROOT)), cacheNomes, integracao);
            lista.add(new GithubKanbanWebhookPessoa(login, nome));
        }
        return lista.isEmpty() ? null : lista;
    }

    public void preencherNomesViaGraphql(
            Long idOrganizacao,
            OrganizacaoGithubIntegracao integracao,
            Set<String> logins,
            Map<String, String> cacheNomes) {
        if (integracao == null || logins == null || logins.isEmpty()) {
            return;
        }
        for (String login : logins) {
            if (!StringUtils.hasText(login)) {
                continue;
            }
            String chave = login.trim().toLowerCase(Locale.ROOT);
            String atual = cacheNomes.get(chave);
            if (StringUtils.hasText(atual) && !login.equalsIgnoreCase(atual)) {
                continue;
            }
            graphqlLoginNameResolver
                    .resolverNome(idOrganizacao, integracao, login.trim())
                    .filter(StringUtils::hasText)
                    .ifPresent(nome -> cacheNomes.put(chave, nome));
        }
    }

    public Set<String> loginsSemNomeLegivel(List<GithubKanbanWebhookPessoa> pessoas) {
        if (pessoas == null) {
            return Set.of();
        }
        return pessoas.stream()
                .filter(p -> p != null && StringUtils.hasText(p.login()))
                .filter(p -> !StringUtils.hasText(p.nome()) || p.login().equalsIgnoreCase(p.nome()))
                .map(p -> p.login().trim())
                .collect(Collectors.toSet());
    }

    private String resolverNome(
            Long idOrganizacao,
            String login,
            String nomeWebhook,
            Map<String, String> cacheNomes,
            OrganizacaoGithubIntegracao integracao) {
        if (StringUtils.hasText(nomeWebhook)) {
            return nomeWebhook.trim();
        }
        String chave = login.toLowerCase(Locale.ROOT);
        String doCache = cacheNomes.get(chave);
        if (StringUtils.hasText(doCache) && !login.equalsIgnoreCase(doCache)) {
            return doCache;
        }
        String daEquipe = responsavelService.resolverNomeExibicaoPorLogin(idOrganizacao, login);
        if (StringUtils.hasText(daEquipe) && !login.equalsIgnoreCase(daEquipe)) {
            cacheNomes.put(chave, daEquipe);
            return daEquipe;
        }
        if (integracao != null) {
            String graphql = graphqlLoginNameResolver.resolverNome(idOrganizacao, integracao, login).orElse(null);
            if (StringUtils.hasText(graphql)) {
                cacheNomes.put(chave, graphql);
                return graphql;
            }
        }
        return login;
    }

    private static String extrairNomeSender(JsonNode root) {
        if (root == null) {
            return null;
        }
        JsonNode sender = root.get("sender");
        if (sender == null || sender.isNull()) {
            return null;
        }
        return texto(sender, "name");
    }

    private static Map<String, String> extrairNomesAssignees(JsonNode root) {
        Map<String, String> mapa = new HashMap<>();
        if (root == null) {
            return mapa;
        }
        JsonNode issue = root.get("issue");
        if (issue == null || issue.isNull()) {
            issue = root.get("pull_request");
        }
        if (issue == null || issue.isNull()) {
            return mapa;
        }
        JsonNode assignees = issue.get("assignees");
        if (assignees != null && assignees.isArray()) {
            for (JsonNode assignee : assignees) {
                adicionarAssignee(mapa, assignee);
            }
        }
        JsonNode assignee = issue.get("assignee");
        if (assignee != null && !assignee.isNull()) {
            adicionarAssignee(mapa, assignee);
        }
        return mapa;
    }

    private static void adicionarAssignee(Map<String, String> mapa, JsonNode assignee) {
        if (assignee == null || assignee.isNull()) {
            return;
        }
        String login = texto(assignee, "login");
        String nome = texto(assignee, "name");
        if (StringUtils.hasText(login) && StringUtils.hasText(nome)) {
            mapa.put(login.trim().toLowerCase(Locale.ROOT), nome.trim());
        }
    }

    private static String texto(JsonNode node, String field) {
        if (node == null || node.isNull()) {
            return null;
        }
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        return value.asText(null);
    }
}
