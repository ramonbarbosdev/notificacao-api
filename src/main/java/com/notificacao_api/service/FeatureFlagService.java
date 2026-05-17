package com.notificacao_api.service;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.notificacao_api.dto.configuracao.FeatureFlagRequest;
import com.notificacao_api.dto.configuracao.FeatureFlagResponse;
import com.notificacao_api.enums.RecursoFeature;
import com.notificacao_api.model.OrganizacaoFeatureFlag;
import com.notificacao_api.repository.OrganizacaoFeatureFlagRepository;

@Service
public class FeatureFlagService {

    private final OrganizacaoFeatureFlagRepository repository;
    private final TenantContextService tenantContextService;
    private final AuditoriaEventoService auditoriaService;

    public FeatureFlagService(
            OrganizacaoFeatureFlagRepository repository,
            TenantContextService tenantContextService,
            AuditoriaEventoService auditoriaService) {
        this.repository = repository;
        this.tenantContextService = tenantContextService;
        this.auditoriaService = auditoriaService;
    }

    @Transactional(readOnly = true)
    public List<FeatureFlagResponse> listarAdmin(Long idOrganizacao) {
        return listarOuPadrao(idOrganizacao);
    }

    @Transactional(readOnly = true)
    public List<FeatureFlagResponse> listarDaOrganizacaoAtual() {
        return listarOuPadrao(tenantContextService.idOrganizacaoObrigatoria());
    }

    @Transactional
    public List<FeatureFlagResponse> atualizar(Long idOrganizacao, FeatureFlagRequest request) {
        Map<RecursoFeature, Boolean> features = request.features();
        if (features == null || features.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe ao menos uma feature.");
        }

        List<FeatureFlagResponse> antes = listarOuPadrao(idOrganizacao);
        features.forEach((recurso, habilitado) -> {
            OrganizacaoFeatureFlag flag = repository.findByIdOrganizacaoAndRecurso(idOrganizacao, recurso)
                    .orElseGet(() -> nova(idOrganizacao, recurso));
            flag.setHabilitado(Boolean.TRUE.equals(habilitado));
            repository.save(flag);
        });
        List<FeatureFlagResponse> depois = listarOuPadrao(idOrganizacao);
        auditoriaService.registrar(idOrganizacao, "FEATURE_FLAG", "ATUALIZAR", "Feature flags alteradas.", antes, depois);
        return depois;
    }

    public void validarRecursoHabilitado(Long idOrganizacao, RecursoFeature recurso) {
        boolean habilitado = repository.findByIdOrganizacaoAndRecurso(idOrganizacao, recurso)
                .map(OrganizacaoFeatureFlag::getHabilitado)
                .orElse(true);
        if (!habilitado) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Recurso " + recurso + " desabilitado para a organizacao.");
        }
    }

    private List<FeatureFlagResponse> listarOuPadrao(Long idOrganizacao) {
        Map<RecursoFeature, OrganizacaoFeatureFlag> existentes = repository.findByIdOrganizacaoOrderByRecursoAsc(idOrganizacao)
                .stream()
                .collect(java.util.stream.Collectors.toMap(OrganizacaoFeatureFlag::getRecurso, item -> item));

        return Arrays.stream(RecursoFeature.values())
                .map(recurso -> existentes.containsKey(recurso)
                        ? toResponse(existentes.get(recurso))
                        : new FeatureFlagResponse(null, idOrganizacao, recurso, true))
                .toList();
    }

    private OrganizacaoFeatureFlag nova(Long idOrganizacao, RecursoFeature recurso) {
        OrganizacaoFeatureFlag flag = new OrganizacaoFeatureFlag();
        flag.setIdOrganizacao(idOrganizacao);
        flag.setRecurso(recurso);
        return flag;
    }

    private FeatureFlagResponse toResponse(OrganizacaoFeatureFlag flag) {
        return new FeatureFlagResponse(
                flag.getIdFeatureFlag(),
                flag.getIdOrganizacao(),
                flag.getRecurso(),
                flag.getHabilitado());
    }
}
