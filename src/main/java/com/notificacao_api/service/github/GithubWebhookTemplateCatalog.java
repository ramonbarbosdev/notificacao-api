package com.notificacao_api.service.github;

import java.util.List;
import java.util.Optional;

import com.notificacao_api.dto.integracao.GithubWebhookTemplateCenarioResponse;
import com.notificacao_api.dto.integracao.GithubWebhookTemplateVariavelResponse;

public final class GithubWebhookTemplateCatalog {

    private GithubWebhookTemplateCatalog() {
    }

    public static final List<GithubWebhookTemplateVariavelResponse> VARIAVEIS = List.of(
            new GithubWebhookTemplateVariavelResponse(
                    "titulo",
                    "Titulo",
                    "Titulo da issue ou descricao do item no Project.",
                    "issue.title; em Project v2 sem issue, fallback em projects_v2_item.content_type",
                    "Corrigir login no app",
                    null),
            new GithubWebhookTemplateVariavelResponse(
                    "status",
                    "Status / coluna",
                    "Coluna de destino, status do campo Status no Project v2, ou acao em eventos de issue.",
                    "project_card.column_name; changes.field_value.to.name; Reordenado/Removido; issues: action",
                    "Em Andamento",
                    "Use com dsGithubStatusDisparo para filtrar notificacoes."),
            new GithubWebhookTemplateVariavelResponse(
                    "acao",
                    "Acao",
                    "Valor do campo action do webhook GitHub.",
                    "action",
                    "edited",
                    null),
            new GithubWebhookTemplateVariavelResponse(
                    "contexto",
                    "Contexto",
                    "Frase fixa que descreve o tipo de evento processado pela API.",
                    "Derivado do tipo de evento (project_card, projects_v2_item, issues)",
                    "Item editado no Project (v2)",
                    null),
            new GithubWebhookTemplateVariavelResponse(
                    "url",
                    "URL",
                    "Link da issue ou PR no GitHub quando disponivel no payload ou via GraphQL (content_node_id).",
                    "issue.html_url; pull_request.html_url; GraphQL node.url",
                    "https://github.com/org/repo/issues/42",
                    null),
            new GithubWebhookTemplateVariavelResponse(
                    "numero",
                    "Numero",
                    "Numero da issue ou pull request no repositorio.",
                    "issue.number; pull_request.number; GraphQL node.number",
                    "123",
                    null),
            new GithubWebhookTemplateVariavelResponse(
                    "evento",
                    "Evento GitHub",
                    "Nome do evento (header X-GitHub-Event).",
                    "X-GitHub-Event",
                    "projects_v2_item",
                    null),
            new GithubWebhookTemplateVariavelResponse(
                    "delivery",
                    "Delivery",
                    "Identificador da entrega do webhook (header X-GitHub-Delivery).",
                    "X-GitHub-Delivery",
                    "abc-123-delivery",
                    null),
            new GithubWebhookTemplateVariavelResponse(
                    "sender",
                    "Quem disparou",
                    "Login de quem moveu, editou ou disparou o evento.",
                    "sender.login",
                    "octocat",
                    "Se nao houver assignee, o destino WhatsApp pode usar o sender."),
            new GithubWebhookTemplateVariavelResponse(
                    "responsaveis",
                    "Responsaveis",
                    "Assignees da issue com prefixo @, separados por virgula.",
                    "issue.assignees; GraphQL assignees.nodes[].login (content_node_id)",
                    "@dev1, @dev2",
                    "Apos um rotulo como Responsaveis:, use esta variavel (nao responsaveis_linha)."),
            new GithubWebhookTemplateVariavelResponse(
                    "responsaveis_linha",
                    "Linha de responsaveis",
                    "Linha pronta com Responsavel(is): @login e quebra de linha, ou vazio.",
                    "Derivado de assignees",
                    "Responsavel(is): @dev1\n",
                    "Legado; prefira {{responsaveis}} com seu proprio texto."),
            new GithubWebhookTemplateVariavelResponse(
                    "url_linha",
                    "Linha da URL",
                    "URL da issue seguida de quebra de linha, ou vazio.",
                    "Derivado de issue.html_url",
                    "https://github.com/org/repo/issues/42\n",
                    null));

    public static final List<GithubWebhookTemplateCenarioResponse> CENARIOS_PREVIEW = List.of(
            new GithubWebhookTemplateCenarioResponse(
                    "projects_v2_edited",
                    "Project v2 — status alterado",
                    "projects_v2_item",
                    "edited",
                    "Mudanca de coluna/campo Status no board v2."),
            new GithubWebhookTemplateCenarioResponse(
                    "projects_v2_reordered",
                    "Project v2 — reordenado",
                    "projects_v2_item",
                    "reordered",
                    "Item reordenado; status pode ser Reordenado se nao houver coluna no payload."),
            new GithubWebhookTemplateCenarioResponse(
                    "projects_v2_deleted",
                    "Project v2 — removido",
                    "projects_v2_item",
                    "deleted",
                    "Item removido do project; status Removido."),
            new GithubWebhookTemplateCenarioResponse(
                    "project_card_moved",
                    "Project classico — card movido",
                    "project_card",
                    "moved",
                    "Cartao movido entre colunas no Project classico."),
            new GithubWebhookTemplateCenarioResponse(
                    "issues_opened",
                    "Issue — aberta",
                    "issues",
                    "opened",
                    "Nova issue aberta no repositorio."));

    public static List<String> chavesVariaveis() {
        return VARIAVEIS.stream().map(GithubWebhookTemplateVariavelResponse::chave).toList();
    }

    public static Optional<GithubWebhookTemplateCenarioResponse> cenarioPorId(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return CENARIOS_PREVIEW.stream().filter(c -> c.id().equals(id)).findFirst();
    }
}
