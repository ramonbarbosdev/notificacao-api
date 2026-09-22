package com.notificacao_api.service;

import java.net.URI;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import com.notificacao_api.model.github.OrganizacaoGithubIntegracao;
import com.notificacao_api.service.github.GithubIntegracaoDefaults;
import com.notificacao_api.service.github.GithubIntegracaoSettings;

@Service
public class OrganizacaoGithubIntegracaoSettingsService {

    public GithubIntegracaoSettings resolver(OrganizacaoGithubIntegracao config) {
        String graphqlUrl = normalizarUrl(
                config != null ? config.getDsGithubGraphqlUrl() : null,
                GithubIntegracaoDefaults.GRAPHQL_URL);
        String apiBaseUrl = normalizarUrl(
                config != null ? config.getDsGithubApiBaseUrl() : null,
                GithubIntegracaoDefaults.API_BASE_URL);
        int connectMs = positivo(
                config != null ? config.getNuGithubHttpConnectTimeoutMs() : null,
                GithubIntegracaoDefaults.HTTP_CONNECT_TIMEOUT_MS);
        int readMs = positivo(
                config != null ? config.getNuGithubHttpReadTimeoutMs() : null,
                GithubIntegracaoDefaults.HTTP_READ_TIMEOUT_MS);
        int skew = skew(
                config != null ? config.getNuGithubInstallationTokenSkewSegundos() : null,
                GithubIntegracaoDefaults.INSTALLATION_TOKEN_SKEW_SEGUNDOS);
        return new GithubIntegracaoSettings(graphqlUrl, apiBaseUrl, connectMs, readMs, skew);
    }

    public void aplicarEndpoints(OrganizacaoGithubIntegracao config, OrganizacaoGithubIntegracaoRequest r) {
        if (r.githubGraphqlUrl() != null) {
            config.setDsGithubGraphqlUrl(normalizarUrlOpcional(r.githubGraphqlUrl()));
        }
        if (r.githubApiBaseUrl() != null) {
            config.setDsGithubApiBaseUrl(normalizarUrlOpcional(r.githubApiBaseUrl()));
        }
        if (r.githubHttpConnectTimeoutMs() != null) {
            config.setNuGithubHttpConnectTimeoutMs(validarTimeout(r.githubHttpConnectTimeoutMs()));
        }
        if (r.githubHttpReadTimeoutMs() != null) {
            config.setNuGithubHttpReadTimeoutMs(validarTimeout(r.githubHttpReadTimeoutMs()));
        }
        if (r.githubInstallationTokenSkewSegundos() != null) {
            config.setNuGithubInstallationTokenSkewSegundos(validarSkew(r.githubInstallationTokenSkewSegundos()));
        }
    }

    private String normalizarUrlOpcional(String url) {
        if (url == null) {
            return null;
        }
        String texto = url.trim();
        if (texto.isEmpty()) {
            return null;
        }
        validarUrlHttp(texto);
        return texto;
    }

    private String normalizarUrl(String valor, String padrao) {
        if (!StringUtils.hasText(valor)) {
            return padrao;
        }
        String texto = valor.trim();
        validarUrlHttp(texto);
        return texto;
    }

    private void validarUrlHttp(String url) {
        try {
            URI uri = URI.create(url);
            if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
                throw new IllegalArgumentException();
            }
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "URL GitHub invalida: " + url);
        }
    }

    private int positivo(Integer valor, int padrao) {
        if (valor == null || valor <= 0) {
            return padrao;
        }
        return valor;
    }

    private int skew(Integer valor, int padrao) {
        if (valor == null || valor < 0) {
            return padrao;
        }
        return valor;
    }

    private Integer validarTimeout(Integer valor) {
        if (valor == null || valor <= 0) {
            return null;
        }
        return valor;
    }

    private Integer validarSkew(Integer valor) {
        if (valor == null || valor < 0) {
            return null;
        }
        return valor;
    }

    /**
     * Campos de rede GitHub vindos do request de configuracao (evita acoplar DTO gigante no service).
     */
    public record OrganizacaoGithubIntegracaoRequest(
            String githubGraphqlUrl,
            String githubApiBaseUrl,
            Integer githubHttpConnectTimeoutMs,
            Integer githubHttpReadTimeoutMs,
            Integer githubInstallationTokenSkewSegundos) {
    }
}
