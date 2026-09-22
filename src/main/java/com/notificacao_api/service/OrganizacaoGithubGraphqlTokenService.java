package com.notificacao_api.service;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.notificacao_api.model.github.OrganizacaoGithubIntegracao;
import com.notificacao_api.security.crypto.EncryptionService;

@Service
public class OrganizacaoGithubGraphqlTokenService {

    private final EncryptionService encryptionService;

    public OrganizacaoGithubGraphqlTokenService(EncryptionService encryptionService) {
        this.encryptionService = encryptionService;
    }

    public boolean estaConfigurado(OrganizacaoGithubIntegracao config) {
        return config != null && StringUtils.hasText(config.getDsGraphqlTokenEnc());
    }

    /**
     * {@code null} não altera; string vazia remove o token armazenado.
     */
    public void aplicar(OrganizacaoGithubIntegracao config, String token) {
        if (token == null) {
            return;
        }
        String normalizado = token.trim();
        if (normalizado.isEmpty()) {
            config.setDsGraphqlTokenEnc(null);
            return;
        }
        config.setDsGraphqlTokenEnc(encryptionService.encrypt(normalizado));
    }

    public String resolverToken(OrganizacaoGithubIntegracao config) {
        if (config == null || !StringUtils.hasText(config.getDsGraphqlTokenEnc())) {
            return null;
        }
        return encryptionService.decrypt(config.getDsGraphqlTokenEnc());
    }
}
