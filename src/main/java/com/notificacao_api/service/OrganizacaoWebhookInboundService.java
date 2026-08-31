package com.notificacao_api.service;

import java.net.URI;

import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import com.notificacao_api.dto.integracao.WhatsappWebhookInboundResponse;
import com.notificacao_api.enums.RecursoFeature;
import com.notificacao_api.model.OrganizacaoConfiguracao;
import com.notificacao_api.security.crypto.EncryptionService;

@Service
public class OrganizacaoWebhookInboundService {

    private final FeatureFlagService featureFlagService;
    private final EncryptionService encryptionService;
    private final Environment environment;

    public OrganizacaoWebhookInboundService(
            FeatureFlagService featureFlagService,
            EncryptionService encryptionService,
            Environment environment) {
        this.featureFlagService = featureFlagService;
        this.encryptionService = encryptionService;
        this.environment = environment;
    }

    public WhatsappWebhookInboundResponse toResponse(OrganizacaoConfiguracao config) {
        return new WhatsappWebhookInboundResponse(
                config.getWebhookInboundUrl(),
                Boolean.TRUE.equals(config.getWebhookInboundHabilitado()),
                StringUtils.hasText(config.getWebhookInboundSecretEnc()));
    }

    public void aplicar(Long idOrganizacao, OrganizacaoConfiguracao config, String url, Boolean habilitado, String secret) {
        boolean ativo = Boolean.TRUE.equals(habilitado);
        if (ativo) {
            featureFlagService.validarRecursoHabilitado(idOrganizacao, RecursoFeature.WEBHOOK);
        }

        String urlNormalizada = normalizarUrl(url);
        if (ativo) {
            validarUrlObrigatoria(urlNormalizada);
            if (!StringUtils.hasText(config.getWebhookInboundSecretEnc())
                    && !StringUtils.hasText(secret)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Informe o secret do webhook inbound ao habilitar o encaminhamento.");
            }
        }

        config.setWebhookInboundUrl(urlNormalizada);
        config.setWebhookInboundHabilitado(ativo);

        if (StringUtils.hasText(secret)) {
            config.setWebhookInboundSecretEnc(encryptionService.encrypt(secret.trim()));
        }
    }

    public String resolverSecret(OrganizacaoConfiguracao config) {
        if (!StringUtils.hasText(config.getWebhookInboundSecretEnc())) {
            return null;
        }
        return encryptionService.decrypt(config.getWebhookInboundSecretEnc());
    }

    public boolean deveEncaminhar(OrganizacaoConfiguracao config) {
        return Boolean.TRUE.equals(config.getWebhookInboundHabilitado())
                && StringUtils.hasText(config.getWebhookInboundUrl())
                && StringUtils.hasText(config.getWebhookInboundSecretEnc());
    }

    private String normalizarUrl(String url) {
        if (!StringUtils.hasText(url)) {
            return null;
        }
        return url.trim();
    }

    private void validarUrlObrigatoria(String url) {
        if (!StringUtils.hasText(url)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Informe a URL do webhook inbound.");
        }
        validarUrl(url);
    }

    private void validarUrl(String url) {
        try {
            URI uri = URI.create(url);
            if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
                throw new IllegalArgumentException();
            }
            if (exigeHttps() && !"https".equalsIgnoreCase(uri.getScheme())) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Em producao, a URL do webhook inbound deve usar HTTPS.");
            }
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "URL do webhook inbound invalida.");
        }
    }

    private boolean exigeHttps() {
        for (String profile : environment.getActiveProfiles()) {
            if ("prod".equalsIgnoreCase(profile) || "production".equalsIgnoreCase(profile)) {
                return true;
            }
        }
        return false;
    }
}
