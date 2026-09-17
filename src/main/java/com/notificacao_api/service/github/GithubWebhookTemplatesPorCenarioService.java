package com.notificacao_api.service.github;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.notificacao_api.dto.configuracao.GithubTemplatePorCenarioDto;
import com.notificacao_api.model.OrganizacaoConfiguracao;

@Service
public class GithubWebhookTemplatesPorCenarioService {

    private static final TypeReference<Map<String, GithubTemplatePorCenarioDto>> MAP_TYPE =
            new TypeReference<>() {};

    private final ObjectMapper objectMapper;

    public GithubWebhookTemplatesPorCenarioService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Map<String, GithubTemplatePorCenarioDto> ler(OrganizacaoConfiguracao config) {
        if (config == null || !StringUtils.hasText(config.getDsGithubTemplatesPorCenario())) {
            return Map.of();
        }
        try {
            Map<String, GithubTemplatePorCenarioDto> mapa =
                    objectMapper.readValue(config.getDsGithubTemplatesPorCenario(), MAP_TYPE);
            return mapa != null ? Map.copyOf(sanitizar(mapa)) : Map.of();
        } catch (Exception ex) {
            return Map.of();
        }
    }

    public void aplicar(OrganizacaoConfiguracao config, Map<String, GithubTemplatePorCenarioDto> entrada) {
        if (config == null || entrada == null) {
            return;
        }
        Map<String, GithubTemplatePorCenarioDto> sanitizado = sanitizar(entrada);
        if (sanitizado.isEmpty()) {
            config.setDsGithubTemplatesPorCenario(null);
            return;
        }
        try {
            config.setDsGithubTemplatesPorCenario(objectMapper.writeValueAsString(sanitizado));
        } catch (Exception ex) {
            throw new IllegalStateException("Falha ao serializar templates GitHub por cenario.", ex);
        }
    }

    public Optional<GithubTemplatePorCenarioDto> resolverPorWebhook(
            OrganizacaoConfiguracao config, String githubEvent, String action) {
        return GithubWebhookTemplateCatalog.cenarioIdPorWebhook(githubEvent, action)
                .flatMap(id -> resolverPorCenarioId(config, id));
    }

    public Optional<GithubTemplatePorCenarioDto> resolverPorCenarioId(
            OrganizacaoConfiguracao config, String cenarioId) {
        if (!StringUtils.hasText(cenarioId)) {
            return Optional.empty();
        }
        GithubTemplatePorCenarioDto dto = ler(config).get(cenarioId.trim());
        if (dto == null) {
            return Optional.empty();
        }
        boolean temAssunto = StringUtils.hasText(dto.assunto());
        boolean temMensagem = StringUtils.hasText(dto.mensagem());
        if (!temAssunto && !temMensagem) {
            return Optional.empty();
        }
        return Optional.of(dto);
    }

    private static Map<String, GithubTemplatePorCenarioDto> sanitizar(Map<String, GithubTemplatePorCenarioDto> entrada) {
        Map<String, GithubTemplatePorCenarioDto> saida = new LinkedHashMap<>();
        for (Map.Entry<String, GithubTemplatePorCenarioDto> entry : entrada.entrySet()) {
            if (!StringUtils.hasText(entry.getKey()) || entry.getValue() == null) {
                continue;
            }
            String assunto = entry.getValue().assunto() != null ? entry.getValue().assunto().trim() : "";
            String mensagem = entry.getValue().mensagem() != null ? entry.getValue().mensagem().trim() : "";
            if (assunto.isEmpty() && mensagem.isEmpty()) {
                continue;
            }
            saida.put(entry.getKey().trim(), new GithubTemplatePorCenarioDto(
                    assunto.isEmpty() ? null : assunto,
                    mensagem.isEmpty() ? null : mensagem));
        }
        return saida;
    }
}
