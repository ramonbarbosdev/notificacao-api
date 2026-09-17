package com.notificacao_api.service;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.notificacao_api.model.OrganizacaoConfiguracao;
import com.notificacao_api.security.crypto.EncryptionService;

@Service
public class OrganizacaoGithubGraphqlTokenService {

    private final EncryptionService encryptionService;

    public OrganizacaoGithubGraphqlTokenService(EncryptionService encryptionService) {
        this.encryptionService = encryptionService;
    }

    public boolean estaConfigurado(OrganizacaoConfiguracao config) {
        return config != null && StringUtils.hasText(config.getDsGithubGraphqlTokenEnc());
    }

    /**
     * {@code null} não altera; string vazia remove o token armazenado.
     */
    public void aplicar(OrganizacaoConfiguracao config, String token) {
        if (token == null) {
            return;
        }
        String normalizado = token.trim();
        if (normalizado.isEmpty()) {
            config.setDsGithubGraphqlTokenEnc(null);
            return;
        }
        config.setDsGithubGraphqlTokenEnc(encryptionService.encrypt(normalizado));
    }

    public String resolverToken(OrganizacaoConfiguracao config) {
        if (config == null || !StringUtils.hasText(config.getDsGithubGraphqlTokenEnc())) {
            return null;
        }
        return encryptionService.decrypt(config.getDsGithubGraphqlTokenEnc());
    }
}
