package com.notificacao_api.service.github;

import java.util.List;
import java.util.Optional;

import com.notificacao_api.dto.integracao.GithubWebhookTemplateCenarioResponse;
import com.notificacao_api.dto.integracao.GithubWebhookTemplateVariavelResponse;

public final class GithubWebhookTemplateCatalog {

    /** Cenário do editor/preview para aviso de PR em status (logins avaliadores). */
    public static final String CENARIO_PR_AVALIADORES = "projects_v2_pr_status";

    private GithubWebhookTemplateCatalog() {
    }

    public static final List<GithubWebhookTemplateVariavelResponse> VARIAVEIS = List.of(
            new GithubWebhookTemplateVariavelResponse(
                    "evento",
                    "Evento (regra)",
                    "Codigo do gatilho de negocio habilitado nas regras.",
                    "Derivado das regras (ex.: STATUS_ALTERADO, TAREFA_CRIADA)",
                    "STATUS_ALTERADO",
                    "Nao e o header X-GitHub-Event; use evento_github para isso."),
            new GithubWebhookTemplateVariavelResponse(
                    "acao",
                    "Acao GitHub",
                    "Valor do campo action do webhook GitHub.",
                    "action",
                    "edited",
                    null),
            new GithubWebhookTemplateVariavelResponse(
                    "titulo",
                    "Titulo",
                    "Titulo da issue ou descricao do item no Project.",
                    "issue.title; em Project v2 sem issue, fallback em projects_v2_item.content_type",
                    "Implementar funcionalidade X",
                    null),
            new GithubWebhookTemplateVariavelResponse(
                    "url",
                    "URL",
                    "Link da issue ou PR no GitHub quando disponivel no payload ou via GraphQL (content_node_id).",
                    "issue.html_url; pull_request.html_url; GraphQL node.url",
                    "https://github.com/gpi-organizacao/esimples-api/issues/123",
                    null),
            new GithubWebhookTemplateVariavelResponse(
                    "status_anterior",
                    "Status anterior",
                    "Coluna/status de origem no Project v2 (campo Status), quando o payload traz changes.field_value.from.",
                    "changes.field_value.from.name",
                    "A Fazer",
                    null),
            new GithubWebhookTemplateVariavelResponse(
                    "status_atual",
                    "Status atual",
                    "Coluna ou valor atual do campo Status apos a movimentacao.",
                    "changes.field_value.to.name; project_card.column_name",
                    "Em Andamento",
                    "Alias de {{status}}."),
            new GithubWebhookTemplateVariavelResponse(
                    "status",
                    "Status / coluna",
                    "Mesmo valor de status_atual (compatibilidade).",
                    "project_card.column_name; changes.field_value.to.name",
                    "Em Andamento",
                    "Use com dsGithubStatusDisparo para filtrar notificacoes."),
            new GithubWebhookTemplateVariavelResponse(
                    "movimentador",
                    "Movimentador",
                    "Login de quem moveu ou editou o card no GitHub.",
                    "sender.login",
                    "ramonbarbosdev",
                    "Alias de {{sender}}."),
            new GithubWebhookTemplateVariavelResponse(
                    "sender",
                    "Quem disparou",
                    "Mesmo login de movimentador (compatibilidade).",
                    "sender.login",
                    "ramonbarbosdev",
                    null),
            new GithubWebhookTemplateVariavelResponse(
                    "responsaveis",
                    "Responsaveis (assignees)",
                    "Assignees do card/issue com prefixo @, separados por virgula.",
                    "issue.assignees; GraphQL assignees.nodes[].login",
                    "@joao, @maria",
                    "Somente quem esta no card, nao quem recebe WhatsApp."),
            new GithubWebhookTemplateVariavelResponse(
                    "responsaveis_plain",
                    "Responsaveis (sem @)",
                    "Assignees do card sem @, separados por virgula.",
                    "Mesma origem de responsaveis",
                    "joao, maria",
                    null),
            new GithubWebhookTemplateVariavelResponse(
                    "destinatarios",
                    "Destinatarios WhatsApp",
                    "Logins GitHub que receberao a mensagem apos regras e opt-in.",
                    "Regras de destinatarios + organizacao_github_responsavel",
                    "joao, maria",
                    "Pode diferir dos assignees do card."),
            new GithubWebhookTemplateVariavelResponse(
                    "contexto",
                    "Contexto",
                    "Frase fixa que descreve o tipo de evento processado pela API.",
                    "Derivado do tipo de evento (project_card, projects_v2_item, issues)",
                    "Item editado no Project (v2)",
                    null),
            new GithubWebhookTemplateVariavelResponse(
                    "numero",
                    "Numero",
                    "Numero da issue ou pull request no repositorio.",
                    "issue.number; pull_request.number; GraphQL node.number",
                    "123",
                    null),
            new GithubWebhookTemplateVariavelResponse(
                    "evento_github",
                    "Evento GitHub (header)",
                    "Nome do evento HTTP (header X-GitHub-Event).",
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
                    "responsaveis_linha",
                    "Linha de responsaveis",
                    "Linha pronta com Responsavel(is): @login e quebra de linha, ou vazio.",
                    "Derivado de assignees",
                    "Responsavel(is): @joao\n",
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
                    "Exemplo STATUS_ALTERADO com status anterior e atual."),
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
        Optional<GithubWebhookTemplateCenarioResponse> encontrado =
                CENARIOS_PREVIEW.stream().filter(c -> c.id().equals(id)).findFirst();
        if (encontrado.isPresent()) {
            return encontrado;
        }
        if (CENARIO_PR_AVALIADORES.equals(id)) {
            return cenarioPorId("projects_v2_edited");
        }
        return Optional.empty();
    }

    /** Mapeia webhook real para o id do cenario de preview/editor. */
    public static Optional<String> cenarioIdPorWebhook(String githubEvent, String action) {
        if (githubEvent == null || githubEvent.isBlank() || action == null || action.isBlank()) {
            return Optional.empty();
        }
        String evento = githubEvent.trim();
        String acao = action.trim();
        return CENARIOS_PREVIEW.stream()
                .filter(c -> c.githubEvent().equals(evento) && c.action().equals(acao))
                .map(GithubWebhookTemplateCenarioResponse::id)
                .findFirst();
    }
}
