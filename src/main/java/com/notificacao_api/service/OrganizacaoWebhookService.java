package com.notificacao_api.service;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.notificacao_api.dto.configuracao.WebhookRequest;
import com.notificacao_api.dto.configuracao.WebhookResponse;
import com.notificacao_api.enums.WebhookEvento;
import com.notificacao_api.model.OrganizacaoWebhook;
import com.notificacao_api.repository.OrganizacaoWebhookRepository;

@Service
public class OrganizacaoWebhookService {

    private final OrganizacaoWebhookRepository repository;
    private final TenantContextService tenantContextService;
    private final PasswordEncoder passwordEncoder;
    private final AuditoriaEventoService auditoriaService;

    public OrganizacaoWebhookService(
            OrganizacaoWebhookRepository repository,
            TenantContextService tenantContextService,
            PasswordEncoder passwordEncoder,
            AuditoriaEventoService auditoriaService) {
        this.repository = repository;
        this.tenantContextService = tenantContextService;
        this.passwordEncoder = passwordEncoder;
        this.auditoriaService = auditoriaService;
    }

    @Transactional(readOnly = true)
    public List<WebhookResponse> listar() {
        Long idOrganizacao = tenantContextService.idOrganizacaoObrigatoria();
        return repository.findByIdOrganizacaoOrderByDtCriacaoDesc(idOrganizacao).stream().map(this::toResponse).toList();
    }

    @Transactional
    public WebhookResponse criar(WebhookRequest request) {
        Long idOrganizacao = tenantContextService.idOrganizacaoObrigatoria();
        OrganizacaoWebhook webhook = new OrganizacaoWebhook();
        webhook.setIdOrganizacao(idOrganizacao);
        aplicar(webhook, request, true);
        WebhookResponse depois = toResponse(repository.save(webhook));
        auditoriaService.registrar(idOrganizacao, "WEBHOOK", "CRIAR", "Webhook criado.", null, depois);
        return depois;
    }

    @Transactional
    public WebhookResponse atualizar(Long idWebhook, WebhookRequest request) {
        Long idOrganizacao = tenantContextService.idOrganizacaoObrigatoria();
        OrganizacaoWebhook webhook = carregar(idOrganizacao, idWebhook);
        WebhookResponse antes = toResponse(webhook);
        aplicar(webhook, request, false);
        WebhookResponse depois = toResponse(repository.save(webhook));
        auditoriaService.registrar(idOrganizacao, "WEBHOOK", "ATUALIZAR", "Webhook atualizado.", antes, depois);
        return depois;
    }

    @Transactional
    public WebhookResponse alterarStatus(Long idWebhook, boolean ativo) {
        Long idOrganizacao = tenantContextService.idOrganizacaoObrigatoria();
        OrganizacaoWebhook webhook = carregar(idOrganizacao, idWebhook);
        webhook.setAtivo(ativo);
        return toResponse(repository.save(webhook));
    }

    @Transactional
    public void remover(Long idWebhook) {
        Long idOrganizacao = tenantContextService.idOrganizacaoObrigatoria();
        OrganizacaoWebhook webhook = carregar(idOrganizacao, idWebhook);
        repository.delete(webhook);
        auditoriaService.registrar(idOrganizacao, "WEBHOOK", "REMOVER", "Webhook removido.", toResponse(webhook), null);
    }

    private OrganizacaoWebhook carregar(Long idOrganizacao, Long idWebhook) {
        return repository.findById(idWebhook)
                .filter(item -> idOrganizacao.equals(item.getIdOrganizacao()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Webhook nao encontrado."));
    }

    private void aplicar(OrganizacaoWebhook webhook, WebhookRequest request, boolean exigirSecret) {
        validarUrl(request.url());
        webhook.setNome(request.nome());
        webhook.setUrl(request.url());
        webhook.setEventos(request.eventos().stream().map(Enum::name).sorted().collect(Collectors.joining(",")));
        webhook.setAtivo(request.ativo() == null || request.ativo());
        if (request.secret() != null && !request.secret().isBlank()) {
            webhook.setSecretHash(passwordEncoder.encode(request.secret()));
        } else if (exigirSecret) {
            webhook.setSecretHash(passwordEncoder.encode(java.util.UUID.randomUUID().toString()));
        }
    }

    private void validarUrl(String url) {
        try {
            URI uri = URI.create(url);
            if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
                throw new IllegalArgumentException();
            }
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "URL do webhook invalida.");
        }
    }

    private Set<WebhookEvento> parseEventos(String eventos) {
        if (eventos == null || eventos.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(eventos.split(",")).map(WebhookEvento::valueOf).collect(Collectors.toSet());
    }

    private WebhookResponse toResponse(OrganizacaoWebhook webhook) {
        return new WebhookResponse(webhook.getIdWebhook(), webhook.getNome(), webhook.getUrl(),
                webhook.getSecretHash() != null, parseEventos(webhook.getEventos()), webhook.getAtivo(),
                webhook.getDtCriacao(), webhook.getDtAtualizacao());
    }
}
