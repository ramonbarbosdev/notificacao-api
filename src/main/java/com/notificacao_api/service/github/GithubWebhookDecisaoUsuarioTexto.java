package com.notificacao_api.service.github;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.util.StringUtils;

import com.notificacao_api.service.github.GithubWebhookRegrasNotificacao.Gatilho;

/**
 * Textos em linguagem clara para o historico de decisoes (frontend / suporte).
 */
public final class GithubWebhookDecisaoUsuarioTexto {

    private GithubWebhookDecisaoUsuarioTexto() {
    }

    public static String explicacaoIgnoradoGatilho(
            String githubEvent, String action, Set<Gatilho> gatilhosDetectados) {
        if (gatilhosDetectados == null || gatilhosDetectados.isEmpty()) {
            return "O GitHub enviou um evento que a integracao nao trata como notificacao "
                    + "(evento " + resumoEvento(githubEvent, action) + "). "
                    + "Ex.: edicao no board sem mudanca do campo Status, ou acao nao mapeada.";
        }
        List<String> nomes = gatilhosDetectados.stream().map(GithubWebhookDecisaoUsuarioTexto::labelGatilho).toList();
        return "O sistema reconheceu o tipo de evento (" + String.join(", ", nomes) + "), mas nenhum gatilho "
                + "equivalente esta ligado em Regras (aba GitHub). Ative o checkbox correspondente e salve.";
    }

    public static String explicacaoIgnoradoStatus(
            String statusDestino,
            String filtroGeral,
            Map<String, Object> avisosAvaliadores,
            Set<Gatilho> gatilhos,
            boolean filtroStatusGeralAplicado) {
        List<String> partes = new ArrayList<>();
        String destinoLegivel = labelStatusDestino(statusDestino);

        if ("assigned".equalsIgnoreCase(StringUtils.trimWhitespace(statusDestino))
                || "unassigned".equalsIgnoreCase(StringUtils.trimWhitespace(statusDestino))) {
            partes.add(
                    "Alteracao de responsavel na issue. O filtro de colunas (geral) so se aplica se "
                            + "\"Responsavel alterado\" ou \"Tarefa atribuida\" estiver marcado em "
                            + "\"Aplicar este filtro de colunas aos gatilhos\" (aba Regras).");
        } else if ("Editado".equals(statusDestino)) {
            partes.add(
                    "O GitHub sinalizou edicao no Project v2 sem nome de coluna Status no payload. "
                            + "Isso costuma ser titulo, assignee ou outro campo — nao arrastar para outra coluna.");
        } else if ("Reordenado".equals(statusDestino)) {
            partes.add("O card foi reordenado no board. Inclua \"Reordenado\" no filtro geral se quiser notificar.");
        }

        if (Boolean.TRUE.equals(avisosAvaliadores.get("issueAvaliadoresNaoAplicado"))) {
            partes.add(
                    "Aviso a avaliadores de Issue nao se aplica: so dispara ao entrar na coluna listada em "
                            + "\"Status da Issue\" (nao em troca de responsavel).");
        }
        if (Boolean.TRUE.equals(avisosAvaliadores.get("prAvaliadoresNaoAplicado"))) {
            partes.add(
                    "Aviso a avaliadores de PR nao se aplica neste evento (coluna de destino fora da lista de PR).");
        }

        if (filtroStatusGeralAplicado && StringUtils.hasText(filtroGeral)) {
            partes.add(
                    "Fluxo geral: a coluna ou status \""
                            + destinoLegivel
                            + "\" nao esta na lista \"Status que geram notificacao (geral)\" ("
                            + filtroGeral
                            + ").");
        } else if (filtroStatusGeralAplicado) {
            partes.add("Fluxo geral bloqueado pelo filtro de colunas (lista geral vazia nao se aplica).");
        }

        if (partes.isEmpty()) {
            partes.add(
                    "Nenhum fluxo (geral ou avaliadores) aceitou o status \""
                            + destinoLegivel
                            + "\" para este evento.");
        }
        return String.join(" ", partes);
    }

    public static String explicacaoEnviado(
            String fluxoDestinatarios,
            int enfileirados,
            Map<String, Object> avisosAvaliadores) {
        String base = enfileirados > 0
                ? "Mensagem enfileirada para " + enfileirados + " numero(s) com opt-in."
                : "Evento processado, mas nenhum WhatsApp foi enfileirado.";
        String fluxo = switch (fluxoDestinatarios != null ? fluxoDestinatarios : "") {
            case "PR_AVALIADORES" -> " Fluxo: avaliadores de PR (logins configurados, nao assignees do card).";
            case "ISSUE_AVALIADORES" -> " Fluxo: avaliadores de Issue (mesmos logins do PR).";
            case "GERAL" -> " Fluxo: regras gerais (gatilhos + destinatarios da aba Regras).";
            default -> "";
        };
        String aviso = "";
        if (Boolean.TRUE.equals(avisosAvaliadores.get("issueAvaliadoresNaoAplicado"))
                || Boolean.TRUE.equals(avisosAvaliadores.get("prAvaliadoresNaoAplicado"))) {
            aviso = " (Avaliadores de PR/Issue nao se aplicaram a este evento; o envio veio do fluxo geral.)";
        }
        return base + fluxo + aviso;
    }

    public static String labelStatusDestino(String statusDestino) {
        if (!StringUtils.hasText(statusDestino)) {
            return "(sem coluna no payload)";
        }
        return switch (statusDestino.trim().toLowerCase(Locale.ROOT)) {
            case "assigned" -> "Responsavel atribuido (assigned)";
            case "unassigned" -> "Responsavel removido (unassigned)";
            case "opened" -> "Issue aberta";
            case "closed" -> "Issue fechada";
            case "reopened" -> "Issue reaberta";
            case "labeled" -> "Label adicionada";
            case "editado" -> "Edicao no board (sem coluna Status)";
            case "reordenado" -> "Reordenacao no board";
            case "removido" -> "Removido do board";
            default -> statusDestino.trim();
        };
    }

    private static String resumoEvento(String githubEvent, String action) {
        String ev = StringUtils.hasText(githubEvent) ? githubEvent : "?";
        String ac = StringUtils.hasText(action) ? action : "?";
        return ev + " / " + ac;
    }

    private static String labelGatilho(Gatilho gatilho) {
        return switch (gatilho) {
            case STATUS_ALTERADO -> "Status alterado";
            case TAREFA_CRIADA -> "Tarefa criada";
            case RESPONSAVEL_ALTERADO -> "Responsavel alterado";
            case TAREFA_ATRIBUIDA -> "Tarefa atribuida";
            case ISSUE_FECHADA_REABERTA -> "Issue fechada/reaberta";
            case ISSUE_LABEL -> "Label na issue";
            case REORDENADO -> "Reordenacao";
        };
    }
}
