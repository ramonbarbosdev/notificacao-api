package com.notificacao_api.service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.notificacao_api.dto.configuracao.ApiKeyCreateRequest;
import com.notificacao_api.dto.configuracao.ApiKeyCreatedResponse;
import com.notificacao_api.dto.configuracao.ApiKeyResponse;
import com.notificacao_api.enums.ApiKeyScope;
import com.notificacao_api.model.OrganizacaoApiKey;
import com.notificacao_api.repository.OrganizacaoApiKeyRepository;

@Service
public class OrganizacaoApiKeyService {

    private final OrganizacaoApiKeyRepository repository;
    private final TenantContextService tenantContextService;
    private final PasswordEncoder passwordEncoder;
    private final AuditoriaEventoService auditoriaService;
    private final SecureRandom secureRandom = new SecureRandom();

    public OrganizacaoApiKeyService(
            OrganizacaoApiKeyRepository repository,
            TenantContextService tenantContextService,
            PasswordEncoder passwordEncoder,
            AuditoriaEventoService auditoriaService) {
        this.repository = repository;
        this.tenantContextService = tenantContextService;
        this.passwordEncoder = passwordEncoder;
        this.auditoriaService = auditoriaService;
    }

    @Transactional(readOnly = true)
    public List<ApiKeyResponse> listar() {
        Long idOrganizacao = tenantContextService.idOrganizacaoObrigatoria();
        return repository.findByIdOrganizacaoOrderByDtCriacaoDesc(idOrganizacao).stream().map(this::toResponse).toList();
    }

    @Transactional
    public ApiKeyCreatedResponse criar(ApiKeyCreateRequest request) {
        Long idOrganizacao = tenantContextService.idOrganizacaoObrigatoria();
        String segredo = gerarSegredo();
        String prefixo = "nak_" + segredo.substring(0, 8);
        String chaveCompleta = prefixo + "." + segredo;

        OrganizacaoApiKey apiKey = new OrganizacaoApiKey();
        apiKey.setIdOrganizacao(idOrganizacao);
        apiKey.setNome(request.nome());
        apiKey.setPrefixo(prefixo);
        apiKey.setHashChave(passwordEncoder.encode(chaveCompleta));
        apiKey.setScopes(joinScopes(request.scopes()));
        apiKey.setExpiraEm(request.expiraEm());
        apiKey = repository.save(apiKey);

        auditoriaService.registrar(idOrganizacao, "API_KEY", "CRIAR", "API Key criada.", null, toResponse(apiKey));

        return new ApiKeyCreatedResponse(
                apiKey.getIdApiKey(),
                apiKey.getNome(),
                apiKey.getPrefixo(),
                chaveCompleta,
                request.scopes(),
                apiKey.getExpiraEm(),
                apiKey.getDtCriacao());
    }

    @Transactional
    public ApiKeyResponse revogar(Long idApiKey) {
        Long idOrganizacao = tenantContextService.idOrganizacaoObrigatoria();
        OrganizacaoApiKey apiKey = repository.findById(idApiKey)
                .filter(item -> idOrganizacao.equals(item.getIdOrganizacao()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "API Key nao encontrada."));
        ApiKeyResponse antes = toResponse(apiKey);
        apiKey.setAtivo(false);
        apiKey.setDtRevogacao(LocalDateTime.now());
        ApiKeyResponse depois = toResponse(repository.save(apiKey));
        auditoriaService.registrar(idOrganizacao, "API_KEY", "REVOGAR", "API Key revogada.", antes, depois);
        return depois;
    }

    private String gerarSegredo() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String joinScopes(Set<ApiKeyScope> scopes) {
        return scopes.stream().map(Enum::name).sorted().collect(Collectors.joining(","));
    }

    private Set<ApiKeyScope> parseScopes(String scopes) {
        if (scopes == null || scopes.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(scopes.split(",")).map(ApiKeyScope::valueOf).collect(Collectors.toSet());
    }

    private ApiKeyResponse toResponse(OrganizacaoApiKey apiKey) {
        return new ApiKeyResponse(
                apiKey.getIdApiKey(), apiKey.getNome(), apiKey.getPrefixo(), parseScopes(apiKey.getScopes()),
                apiKey.getAtivo(), apiKey.getUltimoUsoEm(), apiKey.getExpiraEm(), apiKey.getDtCriacao(),
                apiKey.getDtRevogacao());
    }
}
