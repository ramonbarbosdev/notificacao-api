package com.notificacao_api.service.github;

import java.net.URI;

import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import com.notificacao_api.dto.integracao.github.GithubIntegracaoKanbanMovimentacaoWebhookPatchRequest;
import com.notificacao_api.dto.integracao.github.GithubIntegracaoKanbanMovimentacaoWebhookResponse;
import com.notificacao_api.model.github.OrganizacaoGithubIntegracao;
import com.notificacao_api.security.crypto.EncryptionService;

@Service
public class OrganizacaoGithubKanbanMovimentacaoWebhookService {

    public record ConfiguracaoKanbanMovimentacaoWebhook(
            boolean habilitado, String url, String authorizationHeader, boolean whatsappDiretoHabilitado) {

        public boolean prontaParaEnvio() {
            return habilitado
                    && StringUtils.hasText(url)
                    && StringUtils.hasText(authorizationHeader);
        }
    }

    private final GithubIntegracaoConfigService githubIntegracaoConfigService;
    private final EncryptionService encryptionService;
    private final Environment environment;

    public OrganizacaoGithubKanbanMovimentacaoWebhookService(
            GithubIntegracaoConfigService githubIntegracaoConfigService,
            EncryptionService encryptionService,
            Environment environment) {
        this.githubIntegracaoConfigService = githubIntegracaoConfigService;
        this.encryptionService = encryptionService;
        this.environment = environment;
    }

    public GithubIntegracaoKanbanMovimentacaoWebhookResponse obter(Long idOrganizacao) {
        OrganizacaoGithubIntegracao integracao = githubIntegracaoConfigService.obterIntegracao(idOrganizacao);
        return toResponse(idOrganizacao, integracao);
    }

    public GithubIntegracaoKanbanMovimentacaoWebhookResponse atualizar(
            Long idOrganizacao, GithubIntegracaoKanbanMovimentacaoWebhookPatchRequest request) {
        OrganizacaoGithubIntegracao integracao = githubIntegracaoConfigService.obterIntegracao(idOrganizacao);
        aplicar(integracao, request);
        githubIntegracaoConfigService.salvarIntegracao(integracao);
        return toResponse(idOrganizacao, integracao);
    }

    public ConfiguracaoKanbanMovimentacaoWebhook resolver(OrganizacaoGithubIntegracao integracao) {
        if (integracao == null) {
            return new ConfiguracaoKanbanMovimentacaoWebhook(false, null, null, true);
        }
        boolean habilitado = Boolean.TRUE.equals(integracao.getFlGithubKanbanMovimentacaoWebhookHabilitado());
        boolean whatsappDireto = !Boolean.FALSE.equals(integracao.getFlGithubWhatsappDiretoHabilitado());
        String auth = resolverAuthorization(integracao);
        return new ConfiguracaoKanbanMovimentacaoWebhook(
                habilitado, integracao.getDsGithubKanbanMovimentacaoWebhookUrl(), auth, whatsappDireto);
    }

    public ConfiguracaoKanbanMovimentacaoWebhook resolver(Long idOrganizacao) {
        return resolver(githubIntegracaoConfigService.obterIntegracao(idOrganizacao));
    }

    private void aplicar(
            OrganizacaoGithubIntegracao integracao, GithubIntegracaoKanbanMovimentacaoWebhookPatchRequest request) {
        if (request == null) {
            return;
        }
        if (request.githubWhatsappDiretoHabilitado() != null) {
            integracao.setFlGithubWhatsappDiretoHabilitado(request.githubWhatsappDiretoHabilitado());
        }
        if (request.kanbanMovimentacaoWebhookHabilitado() != null) {
            integracao.setFlGithubKanbanMovimentacaoWebhookHabilitado(request.kanbanMovimentacaoWebhookHabilitado());
        }
        if (request.kanbanMovimentacaoWebhookUrl() != null) {
            integracao.setDsGithubKanbanMovimentacaoWebhookUrl(normalizarUrl(request.kanbanMovimentacaoWebhookUrl()));
        }

        boolean habilitado = Boolean.TRUE.equals(integracao.getFlGithubKanbanMovimentacaoWebhookHabilitado());
        if (habilitado) {
            validarUrlObrigatoria(integracao.getDsGithubKanbanMovimentacaoWebhookUrl());
            if (!StringUtils.hasText(integracao.getDsGithubKanbanMovimentacaoWebhookAuthEnc())
                    && !StringUtils.hasText(request.kanbanMovimentacaoWebhookAuthorization())) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Informe o header Authorization do webhook de movimentacao do kanban ao habilitar.");
            }
        }

        if (StringUtils.hasText(request.kanbanMovimentacaoWebhookAuthorization())) {
            integracao.setDsGithubKanbanMovimentacaoWebhookAuthEnc(
                    encryptionService.encrypt(request.kanbanMovimentacaoWebhookAuthorization().trim()));
        }
    }

    private GithubIntegracaoKanbanMovimentacaoWebhookResponse toResponse(
            Long idOrganizacao, OrganizacaoGithubIntegracao integracao) {
        return new GithubIntegracaoKanbanMovimentacaoWebhookResponse(
                idOrganizacao,
                integracao.getDsGithubKanbanMovimentacaoWebhookUrl(),
                Boolean.TRUE.equals(integracao.getFlGithubKanbanMovimentacaoWebhookHabilitado()),
                StringUtils.hasText(integracao.getDsGithubKanbanMovimentacaoWebhookAuthEnc()),
                !Boolean.FALSE.equals(integracao.getFlGithubWhatsappDiretoHabilitado()));
    }

    private String resolverAuthorization(OrganizacaoGithubIntegracao integracao) {
        if (!StringUtils.hasText(integracao.getDsGithubKanbanMovimentacaoWebhookAuthEnc())) {
            return null;
        }
        return encryptionService.decrypt(integracao.getDsGithubKanbanMovimentacaoWebhookAuthEnc());
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
                    HttpStatus.BAD_REQUEST, "Informe a URL do webhook de movimentacao do kanban.");
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
                        HttpStatus.BAD_REQUEST,
                        "Em producao, a URL do webhook de movimentacao do kanban deve usar HTTPS.");
            }
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "URL do webhook de movimentacao do kanban invalida.");
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
