package com.notificacao_api.service.github;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.notificacao_api.dto.configuracao.GithubTemplatePorCenarioDto;
import com.notificacao_api.dto.integracao.GithubWebhookTemplatePreviewResponse;
import com.notificacao_api.model.github.GithubOrganizacaoConfig;
import com.notificacao_api.shared.TextoTemplateUtil;

@Service
public class GithubWebhookWhatsappTemplateService {

    private final GithubWebhookTemplatesPorCenarioService templatesPorCenarioService;

    public GithubWebhookWhatsappTemplateService(GithubWebhookTemplatesPorCenarioService templatesPorCenarioService) {
        this.templatesPorCenarioService = templatesPorCenarioService;
    }

    public static final String ASSUNTO_PADRAO = "GitHub: {{titulo}}";

    public static final String MENSAGEM_PADRAO = """
            {{contexto}}
            Acao: {{acao}}
            Titulo: {{titulo}}
            Status/Coluna: {{status}}
            {{responsaveis_linha}}{{url_linha}}"""
            .trim();

    public static final String ASSUNTO_PADRAO_PR = "PR para revisão: {{titulo}}";

    public static final String MENSAGEM_PADRAO_PR = """
            Revisão solicitada no GitHub Project
            PR: {{titulo}}
            Status: {{status}}
            Movido por: {{movimentador}}
            Avisar: {{destinatarios}}
            {{url_linha}}"""
            .trim();

    public static final List<String> VARIAVEIS_DISPONIVEIS = GithubWebhookTemplateCatalog.chavesVariaveis();

    private static final Pattern VARIAVEL_PATTERN =
            Pattern.compile("\\{\\{\\s*([\\p{L}_][\\p{L}0-9_.-]*)\\s*}}");

    public MensagemWhatsapp formatar(
            GithubOrganizacaoConfig configuracao,
            String githubEvent,
            String deliveryId,
            GithubWebhookEventoDados dados) {
        return formatar(configuracao, githubEvent, deliveryId, dados, null);
    }

    public MensagemWhatsapp formatar(
            GithubOrganizacaoConfig configuracao,
            String githubEvent,
            String deliveryId,
            GithubWebhookEventoDados dados,
            String cenarioTemplateId) {
        return formatar(configuracao, githubEvent, deliveryId, dados, cenarioTemplateId, null);
    }

    public MensagemWhatsapp formatar(
            GithubOrganizacaoConfig configuracao,
            String githubEvent,
            String deliveryId,
            GithubWebhookEventoDados dados,
            String cenarioTemplateId,
            GithubRegrasPorStatusService.TextoTemplateColuna textoColuna) {

        String assuntoTemplate;
        String mensagemTemplate;
        if (textoColuna != null && StringUtils.hasText(textoColuna.mensagem())) {
            mensagemTemplate = textoColuna.mensagem();
            assuntoTemplate = StringUtils.hasText(textoColuna.assunto())
                    ? textoColuna.assunto()
                    : resolverAssuntoTemplate(configuracao, githubEvent, dados.acao(), cenarioTemplateId);
        } else {
            assuntoTemplate = resolverAssuntoTemplate(configuracao, githubEvent, dados.acao(), cenarioTemplateId);
            mensagemTemplate = resolverMensagemTemplate(configuracao, githubEvent, dados.acao(), cenarioTemplateId);
        }

        return formatarComTemplates(assuntoTemplate, mensagemTemplate, githubEvent, deliveryId, dados);
    }

    private String resolverAssuntoTemplate(
            GithubOrganizacaoConfig configuracao, String githubEvent, String action, String cenarioTemplateId) {
        var porCenario = resolverTemplatePorCenario(configuracao, githubEvent, action, cenarioTemplateId);
        if (porCenario.isPresent() && StringUtils.hasText(porCenario.get().assunto())) {
            return porCenario.get().assunto();
        }
        if (ehCenarioPrAvaliadores(cenarioTemplateId)) {
            return ASSUNTO_PADRAO_PR;
        }
        if (StringUtils.hasText(configuracao.getDsGithubTemplateAssuntoWhatsapp())) {
            return configuracao.getDsGithubTemplateAssuntoWhatsapp();
        }
        return ASSUNTO_PADRAO;
    }

    private String resolverMensagemTemplate(
            GithubOrganizacaoConfig configuracao, String githubEvent, String action, String cenarioTemplateId) {
        var porCenario = resolverTemplatePorCenario(configuracao, githubEvent, action, cenarioTemplateId);
        if (porCenario.isPresent() && StringUtils.hasText(porCenario.get().mensagem())) {
            return porCenario.get().mensagem();
        }
        if (ehCenarioPrAvaliadores(cenarioTemplateId)) {
            return MENSAGEM_PADRAO_PR;
        }
        if (StringUtils.hasText(configuracao.getDsGithubTemplateMensagemWhatsapp())) {
            return configuracao.getDsGithubTemplateMensagemWhatsapp();
        }
        return MENSAGEM_PADRAO;
    }

    private Optional<GithubTemplatePorCenarioDto> resolverTemplatePorCenario(
            GithubOrganizacaoConfig configuracao,
            String githubEvent,
            String action,
            String cenarioTemplateId) {
        if (StringUtils.hasText(cenarioTemplateId)) {
            return templatesPorCenarioService.resolverPorCenarioId(configuracao, cenarioTemplateId);
        }
        return templatesPorCenarioService.resolverPorWebhook(configuracao, githubEvent, action);
    }

    private static boolean ehCenarioPrAvaliadores(String cenarioTemplateId) {
        return GithubWebhookTemplateCatalog.CENARIO_PR_AVALIADORES.equals(cenarioTemplateId);
    }

    public MensagemWhatsapp formatarComTemplates(
            String assuntoTemplate,
            String mensagemTemplate,
            String githubEvent,
            String deliveryId,
            GithubWebhookEventoDados dados) {

        Map<String, String> variaveis = montarVariaveis(githubEvent, deliveryId, dados);

        String assuntoTpl = StringUtils.hasText(assuntoTemplate) ? assuntoTemplate : ASSUNTO_PADRAO;
        String mensagemTpl = StringUtils.hasText(mensagemTemplate) ? mensagemTemplate : MENSAGEM_PADRAO;

        String assunto = limpar(TextoTemplateUtil.substituir(assuntoTpl, variaveis));
        String mensagem = limpar(TextoTemplateUtil.substituir(mensagemTpl, variaveis));

        if (!StringUtils.hasText(mensagem)) {
            mensagem = limpar(TextoTemplateUtil.substituir(MENSAGEM_PADRAO, variaveis));
        }
        if (!StringUtils.hasText(assunto)) {
            assunto = limpar(TextoTemplateUtil.substituir(ASSUNTO_PADRAO, variaveis));
        }

        return new MensagemWhatsapp(assunto, mensagem, textoWhatsappFinal(assunto, mensagem));
    }

    public GithubWebhookTemplatePreviewResponse preview(String templateAssunto, String templateMensagem, String cenarioId) {
        var cenario = GithubWebhookTemplateCatalog.cenarioPorId(cenarioId)
                .orElseThrow(() -> new IllegalArgumentException("Cenario de preview desconhecido: " + cenarioId));

        GithubWebhookEventoDados dados = dadosExemploPorCenario(cenario.id());
        String githubEvent = cenario.githubEvent();
        String deliveryId = "preview-delivery-id";

        MensagemWhatsapp msg = formatarComTemplates(templateAssunto, templateMensagem, githubEvent, deliveryId, dados);
        Map<String, String> variaveisUsadas = montarVariaveis(githubEvent, deliveryId, dados);
        List<String> desconhecidas = variaveisDesconhecidas(templateAssunto, templateMensagem);

        return new GithubWebhookTemplatePreviewResponse(
                msg.assunto(),
                msg.mensagem(),
                msg.textoWhatsapp(),
                variaveisUsadas,
                desconhecidas,
                montarContextoEvento(githubEvent, deliveryId, dados));
    }

    public List<String> variaveisDesconhecidas(String templateAssunto, String templateMensagem) {
        Set<String> conhecidas = new LinkedHashSet<>(VARIAVEIS_DISPONIVEIS);
        Set<String> encontradas = new LinkedHashSet<>();
        coletarPlaceholders(templateAssunto, encontradas);
        coletarPlaceholders(templateMensagem, encontradas);
        List<String> desconhecidas = new ArrayList<>();
        for (String nome : encontradas) {
            if (!conhecidas.contains(nome)) {
                desconhecidas.add(nome);
            }
        }
        return desconhecidas;
    }

    private void coletarPlaceholders(String texto, Set<String> destino) {
        if (!StringUtils.hasText(texto)) {
            return;
        }
        String normalizado = TextoTemplateUtil.normalizarMarcadoresTemplate(texto);
        Matcher matcher = VARIAVEL_PATTERN.matcher(normalizado);
        while (matcher.find()) {
            destino.add(matcher.group(1));
        }
    }

    private GithubWebhookEventoDados dadosExemploPorCenario(String cenarioId) {
        return switch (cenarioId) {
            case GithubWebhookTemplateCatalog.CENARIO_PR_AVALIADORES -> new GithubWebhookEventoDados(
                    "feat: autenticacao OAuth",
                    "Em revisão",
                    "Em andamento",
                    "Pull Request atualizado no Project (v2)",
                    "edited",
                    "https://github.com/gpi-organizacao/esimples-api/pull/42",
                    "dev-autor",
                    List.of(),
                    List.of("reviewer1", "tech-lead"),
                    "PR_AVALIADORES",
                    42);
            case "projects_v2_edited" -> new GithubWebhookEventoDados(
                    "Implementar funcionalidade X",
                    "Em Andamento",
                    "A Fazer",
                    "Item editado no Project (v2)",
                    "edited",
                    "https://github.com/gpi-organizacao/esimples-api/issues/123",
                    "ramonbarbosdev",
                    List.of("joao", "maria"),
                    List.of("joao", "maria"),
                    "STATUS_ALTERADO",
                    123);
            case "projects_v2_reordered" -> new GithubWebhookEventoDados(
                    "Refatorar modulo de fila",
                    "Reordenado",
                    null,
                    "Item reordenado no Project (v2)",
                    "reordered",
                    "https://github.com/org/repo/issues/7",
                    "octocat",
                    List.of(),
                    List.of(),
                    "REORDENADO",
                    7);
            case "projects_v2_deleted" -> new GithubWebhookEventoDados(
                    "Card obsoleto",
                    "Removido",
                    "Em Andamento",
                    "Item removido do Project (v2)",
                    "deleted",
                    null,
                    "octocat",
                    List.of(),
                    List.of(),
                    "STATUS_ALTERADO",
                    null);
            case "project_card_moved" -> new GithubWebhookEventoDados(
                    "Deploy producao",
                    "Review",
                    "Em Andamento",
                    "Cartao movido no Project (classico)",
                    "moved",
                    "https://github.com/org/repo/issues/99",
                    "devops-user",
                    List.of("devops-user"),
                    List.of("devops-user"),
                    "STATUS_ALTERADO",
                    99);
            case "issues_opened" -> new GithubWebhookEventoDados(
                    "Bug no checkout",
                    "opened",
                    null,
                    "Issue atualizada",
                    "opened",
                    "https://github.com/org/repo/issues/100",
                    "reporter",
                    List.of("assignee1"),
                    List.of("assignee1"),
                    "TAREFA_CRIADA",
                    100);
            default -> throw new IllegalArgumentException("Cenario de preview desconhecido: " + cenarioId);
        };
    }

    /**
     * WhatsApp envia apenas o corpo ({@code Notificacao.mensagem}); o assunto da fila nao vai no texto.
     */
    public String textoWhatsappFinal(String assunto, String mensagem) {
        String corpo = StringUtils.hasText(mensagem) ? mensagem.trim() : "";
        String tituloCurto = StringUtils.hasText(assunto) ? assunto.trim() : "";
        if (corpo.isEmpty()) {
            return tituloCurto;
        }
        if (tituloCurto.isEmpty()) {
            return corpo;
        }
        if (corpo.startsWith(tituloCurto)) {
            return corpo;
        }
        return tituloCurto + "\n\n" + corpo;
    }

    Map<String, Object> montarContextoEvento(String githubEvent, String deliveryId, GithubWebhookEventoDados dados) {
        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("evento", vazio(dados.codigoGatilho()));
        ctx.put("acao", vazio(dados.acao()));
        ctx.put("titulo", vazio(dados.titulo()));
        ctx.put("url", vazio(dados.url()));
        ctx.put("status_anterior", vazio(dados.statusAnterior()));
        ctx.put("status_atual", vazio(dados.statusDestino()));
        ctx.put("movimentador", vazio(dados.senderLogin()));
        ctx.put("responsaveis", List.copyOf(dados.assigneesLogins()));
        ctx.put("destinatarios", List.copyOf(dados.destinatariosLogins()));
        ctx.put("evento_github", vazio(githubEvent));
        ctx.put("delivery", vazio(deliveryId));
        ctx.put("numero", dados.numero() != null ? dados.numero() : null);
        return ctx;
    }

    private Map<String, String> montarVariaveis(String githubEvent, String deliveryId, GithubWebhookEventoDados dados) {
        Map<String, String> variaveis = new LinkedHashMap<>();
        variaveis.put("titulo", vazio(dados.titulo()));
        variaveis.put("status", vazio(dados.statusDestino()));
        variaveis.put("status_atual", vazio(dados.statusDestino()));
        variaveis.put("status_anterior", vazio(dados.statusAnterior()));
        variaveis.put("acao", vazio(dados.acao()));
        variaveis.put("contexto", vazio(dados.contexto()));
        variaveis.put("url", vazio(dados.url()));
        variaveis.put("numero", dados.numero() != null ? dados.numero().toString() : "");
        variaveis.put("evento", vazio(dados.codigoGatilho()));
        variaveis.put("evento_github", vazio(githubEvent));
        variaveis.put("delivery", vazio(deliveryId));
        variaveis.put("sender", vazio(dados.senderLogin()));
        variaveis.put("movimentador", vazio(dados.senderLogin()));

        String responsaveisAt = formatarLoginsComArroba(dados.assigneesLogins());
        String responsaveisPlain = formatarLoginsPlain(dados.assigneesLogins());
        String destinatariosPlain = formatarLoginsPlain(dados.destinatariosLogins());

        variaveis.put("responsaveis", responsaveisAt);
        variaveis.put("responsaveis_plain", responsaveisPlain);
        variaveis.put("destinatarios", destinatariosPlain);
        variaveis.put(
                "responsaveis_linha",
                responsaveisAt.isEmpty() ? "" : "Responsavel(is): " + responsaveisAt + "\n");
        variaveis.put("url_linha", StringUtils.hasText(dados.url()) ? dados.url() + "\n" : "");
        return variaveis;
    }

    private static String formatarLoginsComArroba(List<String> logins) {
        if (logins == null || logins.isEmpty()) {
            return "";
        }
        return logins.stream()
                .filter(StringUtils::hasText)
                .map(login -> "@" + login.trim())
                .collect(Collectors.joining(", "));
    }

    private static String formatarLoginsPlain(List<String> logins) {
        if (logins == null || logins.isEmpty()) {
            return "";
        }
        return logins.stream()
                .filter(StringUtils::hasText)
                .map(login -> login.trim())
                .collect(Collectors.joining(", "));
    }

    private String vazio(String valor) {
        return valor != null ? valor : "";
    }

    private String limpar(String texto) {
        if (texto == null) {
            return "";
        }
        return texto.replaceAll("(?m)[ \t]+\n", "\n").replaceAll("\n{3,}", "\n\n").trim();
    }

    public record MensagemWhatsapp(String assunto, String mensagem, String textoWhatsapp) {
    }

    public record GithubWebhookEventoDados(
            String titulo,
            String statusDestino,
            String statusAnterior,
            String contexto,
            String acao,
            String url,
            String senderLogin,
            List<String> assigneesLogins,
            List<String> destinatariosLogins,
            String codigoGatilho,
            Integer numero) {

        /** Compatibilidade: mesma lista em assignees e destinatarios. */
        public GithubWebhookEventoDados(
                String titulo,
                String statusDestino,
                String contexto,
                String acao,
                String url,
                String senderLogin,
                List<String> githubLogins,
                Integer numero) {
            this(
                    titulo,
                    statusDestino,
                    null,
                    contexto,
                    acao,
                    url,
                    senderLogin,
                    githubLogins != null ? List.copyOf(githubLogins) : List.of(),
                    githubLogins != null ? List.copyOf(githubLogins) : List.of(),
                    null,
                    numero);
        }
    }
}
