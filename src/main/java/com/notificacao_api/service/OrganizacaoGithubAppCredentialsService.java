package com.notificacao_api.service;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.notificacao_api.model.OrganizacaoConfiguracao;
import com.notificacao_api.security.crypto.EncryptionService;
import com.notificacao_api.service.github.app.GithubAppCredentials;

@Service
public class OrganizacaoGithubAppCredentialsService {

    private final EncryptionService encryptionService;

    public OrganizacaoGithubAppCredentialsService(EncryptionService encryptionService) {
        this.encryptionService = encryptionService;
    }

    public boolean estaConfigurado(OrganizacaoConfiguracao config) {
        return config != null
                && config.getNuGithubAppId() != null
                && config.getNuGithubAppId() > 0
                && StringUtils.hasText(config.getDsGithubAppPrivateKeyEnc());
    }

    public boolean privateKeyConfigurada(OrganizacaoConfiguracao config) {
        return config != null && StringUtils.hasText(config.getDsGithubAppPrivateKeyEnc());
    }

    public void aplicarAppId(OrganizacaoConfiguracao config, Long appId) {
        if (appId == null || appId <= 0) {
            config.setNuGithubAppId(null);
            return;
        }
        config.setNuGithubAppId(appId);
    }

    public void aplicarInstallationId(OrganizacaoConfiguracao config, Long installationId) {
        if (installationId == null || installationId <= 0) {
            config.setNuGithubInstallationId(null);
            return;
        }
        config.setNuGithubInstallationId(installationId);
    }

    /**
     * {@code null} não altera; string vazia remove a chave armazenada.
     */
    public void aplicarPrivateKeyPem(OrganizacaoConfiguracao config, String privateKeyPem) {
        if (privateKeyPem == null) {
            return;
        }
        String normalizado = privateKeyPem.trim();
        if (normalizado.isEmpty()) {
            config.setDsGithubAppPrivateKeyEnc(null);
            return;
        }
        config.setDsGithubAppPrivateKeyEnc(encryptionService.encrypt(normalizado));
    }

    public Optional<GithubAppCredentials> resolverCredenciais(OrganizacaoConfiguracao config) {
        if (!estaConfigurado(config)) {
            return Optional.empty();
        }
        String pem = encryptionService.decrypt(config.getDsGithubAppPrivateKeyEnc());
        if (!StringUtils.hasText(pem)) {
            return Optional.empty();
        }
        return Optional.of(new GithubAppCredentials(config.getNuGithubAppId(), pem.trim()));
    }
}
