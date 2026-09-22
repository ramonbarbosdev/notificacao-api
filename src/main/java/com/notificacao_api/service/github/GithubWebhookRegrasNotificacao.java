package com.notificacao_api.service.github;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.util.StringUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.notificacao_api.enums.GithubDestinatariosModo;
import com.notificacao_api.model.github.GithubOrganizacaoConfig;

public final class GithubWebhookRegrasNotificacao {

    public enum Gatilho {
        STATUS_ALTERADO,
        TAREFA_CRIADA,
        RESPONSAVEL_ALTERADO,
        TAREFA_ATRIBUIDA,
        ISSUE_FECHADA_REABERTA,
        ISSUE_LABEL,
        REORDENADO
    }

    private GithubWebhookRegrasNotificacao() {
    }

    /** Código usado em {{evento}} nos templates (ex.: STATUS_ALTERADO). */
    public static String codigoPrincipal(Set<Gatilho> gatilhos) {
        if (gatilhos == null || gatilhos.isEmpty()) {
            return "";
        }
        if (gatilhos.contains(Gatilho.STATUS_ALTERADO)) {
            return Gatilho.STATUS_ALTERADO.name();
        }
        return gatilhos.iterator().next().name();
    }

    public static boolean deveNotificarPorGatilho(GithubOrganizacaoConfig config, Set<Gatilho> gatilhos) {
        if (gatilhos == null || gatilhos.isEmpty()) {
            return false;
        }
        for (Gatilho gatilho : gatilhos) {
            if (gatilhoHabilitado(config, gatilho)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Aplica {@code dsGithubStatusDisparo} apenas aos gatilhos listados em
     * {@code dsGithubStatusDisparoGatilhos} (virgula). NULL no banco = STATUS_ALTERADO e REORDENADO.
     */
    public static boolean deveAplicarFiltroStatusColunaGeral(
            GithubOrganizacaoConfig config, Set<Gatilho> gatilhosDetectados) {
        if (gatilhosDetectados == null || gatilhosDetectados.isEmpty()) {
            return false;
        }
        Set<Gatilho> comFiltro = gatilhosComFiltroStatusColunaGeral(config);
        for (Gatilho detectado : gatilhosDetectados) {
            if (comFiltro.contains(detectado) && gatilhoHabilitado(config, detectado)) {
                return true;
            }
        }
        return false;
    }

    public static Set<Gatilho> gatilhosComFiltroStatusColunaGeral(GithubOrganizacaoConfig config) {
        String raw = config != null ? config.getDsGithubStatusDisparoGatilhos() : null;
        if (!StringUtils.hasText(raw)) {
            return Set.of(Gatilho.STATUS_ALTERADO, Gatilho.REORDENADO);
        }
        Set<Gatilho> parsed = new LinkedHashSet<>();
        for (String parte : raw.split("[,;]+")) {
            String token = parte.trim();
            if (!StringUtils.hasText(token)) {
                continue;
            }
            try {
                parsed.add(Gatilho.valueOf(token));
            } catch (IllegalArgumentException ignored) {
                // ignora token desconhecido (versao antiga do front)
            }
        }
        if (parsed.isEmpty()) {
            return Set.of(Gatilho.STATUS_ALTERADO, Gatilho.REORDENADO);
        }
        return Set.copyOf(parsed);
    }

    public static Set<Gatilho> classificarGatilhos(
            GithubOrganizacaoConfig config, String githubEvent, String action, JsonNode root) {
        if (!StringUtils.hasText(githubEvent) || !StringUtils.hasText(action)) {
            return Set.of();
        }
        String evento = githubEvent.trim();
        String acao = action.trim();

        if ("project_card".equals(evento)) {
            if ("moved".equals(acao)) {
                return Set.of(Gatilho.STATUS_ALTERADO);
            }
            if ("created".equals(acao)) {
                return Set.of(Gatilho.TAREFA_CRIADA);
            }
            return Set.of();
        }

        if ("projects_v2_item".equals(evento)) {
            if ("deleted".equals(acao)) {
                return Set.of(Gatilho.STATUS_ALTERADO);
            }
            if ("reordered".equals(acao)) {
                return Set.of(Gatilho.REORDENADO);
            }
            if ("edited".equals(acao)) {
                if (!mudouCampoStatus(root)) {
                    return Set.of();
                }
                return Set.of(Gatilho.STATUS_ALTERADO);
            }
            return Set.of();
        }

        if ("issues".equals(evento)) {
            return switch (acao) {
                case "opened" -> Set.of(Gatilho.TAREFA_CRIADA);
                case "assigned" -> EnumSet.of(Gatilho.TAREFA_ATRIBUIDA, Gatilho.RESPONSAVEL_ALTERADO);
                case "unassigned" -> Set.of(Gatilho.RESPONSAVEL_ALTERADO);
                case "closed", "reopened" -> Set.of(Gatilho.ISSUE_FECHADA_REABERTA);
                case "labeled" -> Set.of(Gatilho.ISSUE_LABEL);
                default -> Set.of();
            };
        }

        return Set.of();
    }

    private static boolean mudouCampoStatus(JsonNode root) {
        if (root == null || root.isNull()) {
            return false;
        }
        JsonNode changes = root.get("changes");
        if (changes == null || changes.isNull()) {
            return false;
        }
        JsonNode fieldValue = changes.get("field_value");
        if (fieldValue == null || fieldValue.isNull()) {
            return false;
        }
        JsonNode fieldName = fieldValue.get("field_name");
        if (fieldName != null && !fieldName.isNull()) {
            String nome = fieldName.asText("");
            if (StringUtils.hasText(nome) && !"status".equalsIgnoreCase(nome.trim())) {
                return false;
            }
        }
        JsonNode to = fieldValue.get("to");
        return to != null && !to.isNull();
    }

    public static List<String> resolverLoginsDestino(
            GithubOrganizacaoConfig config,
            String githubEvent,
            JsonNode root,
            List<String> loginsAssignees,
            String senderLogin) {

        List<String> loginsAssigneesNormalizados = normalizarLogins(loginsAssignees);
        GithubDestinatariosModo modo = GithubDestinatariosModo.fromString(config.getDsGithubDestinatariosModo());
        List<String> logins = new ArrayList<>();

        switch (modo) {
            case LOGINS_CONFIGURADOS -> logins.addAll(parseLoginsExtras(config.getDsGithubDestinatariosExtras()));
            case RESPONSAVEIS_E_MOVIMENTADOR -> {
                logins.addAll(loginsAssignees);
                adicionarLogin(logins, senderLogin);
            }
            default -> {
                logins.addAll(loginsAssignees);
                if (logins.isEmpty() && !ignorarSemResponsavel(config)) {
                    if ("projects_v2_item".equals(githubEvent) || "project_card".equals(githubEvent)) {
                        adicionarLogin(logins, senderLogin);
                    }
                }
            }
        }

        if (naoNotificarMovimentador(config)) {
            removerMovimentadorSeNaoForAssignee(logins, senderLogin, loginsAssigneesNormalizados);
        }

        return deduplicar(logins);
    }

    /**
     * Remove quem moveu/editou, exceto quando também é assignee da tarefa (issue/GraphQL).
     */
    private static void removerMovimentadorSeNaoForAssignee(
            List<String> logins, String senderLogin, List<String> loginsAssigneesNormalizados) {
        if (!StringUtils.hasText(senderLogin)) {
            return;
        }
        String sender = senderLogin.trim().toLowerCase(Locale.ROOT);
        if (loginsAssigneesNormalizados.stream().anyMatch(a -> a.equalsIgnoreCase(sender))) {
            return;
        }
        removerLogin(logins, senderLogin);
    }

    private static List<String> normalizarLogins(List<String> logins) {
        if (logins == null || logins.isEmpty()) {
            return List.of();
        }
        List<String> normalizados = new ArrayList<>();
        for (String login : logins) {
            adicionarLogin(normalizados, login);
        }
        return List.copyOf(normalizados);
    }

    public static boolean ignorarSemResponsavel(GithubOrganizacaoConfig config) {
        if (config.getGithubIgnorarSemResponsavel() == null) {
            return true;
        }
        return config.getGithubIgnorarSemResponsavel();
    }

    private static boolean gatilhoHabilitado(GithubOrganizacaoConfig config, Gatilho gatilho) {
        return switch (gatilho) {
            case STATUS_ALTERADO -> flag(config.getGithubNotificarStatusAlterado(), true);
            case TAREFA_CRIADA -> flag(config.getGithubNotificarTarefaCriada(), false);
            case RESPONSAVEL_ALTERADO -> flag(config.getGithubNotificarResponsavelAlterado(), false);
            case TAREFA_ATRIBUIDA -> flag(config.getGithubNotificarTarefaAtribuida(), false);
            case ISSUE_FECHADA_REABERTA -> flag(config.getGithubNotificarIssueFechadaReaberta(), false);
            case ISSUE_LABEL -> flag(config.getGithubNotificarIssueLabel(), false);
            case REORDENADO -> flag(config.getGithubNotificarReordenacao(), false);
        };
    }

    private static boolean flag(Boolean valor, boolean padrao) {
        return valor != null ? valor : padrao;
    }

    private static boolean naoNotificarMovimentador(GithubOrganizacaoConfig config) {
        return flag(config.getGithubNaoNotificarMovimentador(), true);
    }

    public static List<String> parseLoginsLista(String extras) {
        if (!StringUtils.hasText(extras)) {
            return List.of();
        }
        return Arrays.stream(extras.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> s.toLowerCase(Locale.ROOT))
                .collect(Collectors.toList());
    }

    private static List<String> parseLoginsExtras(String extras) {
        return parseLoginsLista(extras);
    }

    private static void adicionarLogin(List<String> logins, String login) {
        if (!StringUtils.hasText(login)) {
            return;
        }
        String normalizado = login.trim().toLowerCase(Locale.ROOT);
        if (!logins.contains(normalizado)) {
            logins.add(normalizado);
        }
    }

    private static void removerLogin(List<String> logins, String login) {
        if (!StringUtils.hasText(login)) {
            return;
        }
        String normalizado = login.trim().toLowerCase(Locale.ROOT);
        logins.removeIf(l -> l.equalsIgnoreCase(normalizado));
    }

    private static List<String> deduplicar(List<String> logins) {
        return List.copyOf(new LinkedHashSet<>(logins));
    }
}
