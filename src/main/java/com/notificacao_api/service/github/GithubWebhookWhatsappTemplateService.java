package com.notificacao_api.service.github;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.notificacao_api.model.OrganizacaoConfiguracao;
import com.notificacao_api.shared.TextoTemplateUtil;

@Service
public class GithubWebhookWhatsappTemplateService {

    public static final String ASSUNTO_PADRAO = "GitHub: {{titulo}}";

    public static final String MENSAGEM_PADRAO = """
            {{contexto}}
            Acao: {{acao}}
            Titulo: {{titulo}}
            Status/Coluna: {{status}}
            {{responsaveis_linha}}{{url_linha}}"""
            .trim();

    public static final List<String> VARIAVEIS_DISPONIVEIS = List.of(
            "titulo",
            "status",
            "acao",
            "contexto",
            "url",
            "evento",
            "delivery",
            "sender",
            "responsaveis",
            "responsaveis_linha",
            "url_linha");

    public MensagemWhatsapp formatar(
            OrganizacaoConfiguracao configuracao,
            String githubEvent,
            String deliveryId,
            GithubWebhookEventoDados dados) {

        Map<String, String> variaveis = montarVariaveis(githubEvent, deliveryId, dados);

        String assuntoTemplate = StringUtils.hasText(configuracao.getDsGithubTemplateAssuntoWhatsapp())
                ? configuracao.getDsGithubTemplateAssuntoWhatsapp()
                : ASSUNTO_PADRAO;
        String mensagemTemplate = StringUtils.hasText(configuracao.getDsGithubTemplateMensagemWhatsapp())
                ? configuracao.getDsGithubTemplateMensagemWhatsapp()
                : MENSAGEM_PADRAO;

        String assunto = limpar(TextoTemplateUtil.substituir(assuntoTemplate, variaveis));
        String mensagem = limpar(TextoTemplateUtil.substituir(mensagemTemplate, variaveis));

        if (!StringUtils.hasText(mensagem)) {
            mensagem = limpar(TextoTemplateUtil.substituir(MENSAGEM_PADRAO, variaveis));
        }
        if (!StringUtils.hasText(assunto)) {
            assunto = limpar(TextoTemplateUtil.substituir(ASSUNTO_PADRAO, variaveis));
        }

        return new MensagemWhatsapp(assunto, mensagem);
    }

    private Map<String, String> montarVariaveis(String githubEvent, String deliveryId, GithubWebhookEventoDados dados) {
        Map<String, String> variaveis = new LinkedHashMap<>();
        variaveis.put("titulo", vazio(dados.titulo()));
        variaveis.put("status", vazio(dados.statusDestino()));
        variaveis.put("acao", vazio(dados.acao()));
        variaveis.put("contexto", vazio(dados.contexto()));
        variaveis.put("url", vazio(dados.url()));
        variaveis.put("evento", vazio(githubEvent));
        variaveis.put("delivery", vazio(deliveryId));
        variaveis.put("sender", vazio(dados.senderLogin()));

        String responsaveis = dados.githubLogins().isEmpty()
                ? ""
                : dados.githubLogins().stream().map(login -> "@" + login).collect(Collectors.joining(", "));
        variaveis.put("responsaveis", responsaveis);
        variaveis.put(
                "responsaveis_linha",
                responsaveis.isEmpty() ? "" : "Responsavel(is): " + responsaveis + "\n");
        variaveis.put("url_linha", StringUtils.hasText(dados.url()) ? dados.url() + "\n" : "");
        return variaveis;
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

    public record MensagemWhatsapp(String assunto, String mensagem) {
    }

    public record GithubWebhookEventoDados(
            String titulo,
            String statusDestino,
            String contexto,
            String acao,
            String url,
            String senderLogin,
            List<String> githubLogins) {
    }
}
