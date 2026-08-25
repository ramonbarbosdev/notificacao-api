package com.notificacao_api.service;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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

        Map<RecursoFeature, Boolean> estadoAtual = estadoAtual(idOrganizacao);
        Map<RecursoFeature, Boolean> estadoFinal = new EnumMap<>(estadoAtual);
        estadoFinal.putAll(features);

        aplicarExclusividadeMotoresWhatsapp(estadoFinal);

        List<FeatureFlagResponse> antes = listarOuPadrao(idOrganizacao);
        estadoFinal.forEach((recurso, habilitado) -> salvarFlag(idOrganizacao, recurso, habilitado));
        List<FeatureFlagResponse> depois = listarOuPadrao(idOrganizacao);
        auditoriaService.registrar(idOrganizacao, "FEATURE_FLAG", "ATUALIZAR", "Feature flags alteradas.", antes, depois);
        return depois;
    }

    public void validarRecursoHabilitado(Long idOrganizacao, RecursoFeature recurso) {
        if (recurso == RecursoFeature.WHATSAPP) {
            validarMotorWhatsappHabilitado(idOrganizacao);
            return;
        }
        if (!recursoHabilitado(idOrganizacao, recurso)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Recurso " + recurso + " desabilitado para a organizacao.");
        }
    }

    public void validarMotorWhatsappHabilitado(Long idOrganizacao) {
        motorWhatsappHabilitado(idOrganizacao)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Nenhum motor WhatsApp habilitado para a organizacao. Habilite Gateway ou Meta Cloud."));
    }

    public Optional<RecursoFeature> motorWhatsappHabilitado(Long idOrganizacao) {
        boolean gateway = recursoHabilitado(idOrganizacao, RecursoFeature.WHATSAPP_GATEWAY);
        boolean meta = recursoHabilitado(idOrganizacao, RecursoFeature.WHATSAPP_META_CLOUD);

        if (gateway && meta) {
            return Optional.of(RecursoFeature.WHATSAPP_GATEWAY);
        }
        if (meta) {
            return Optional.of(RecursoFeature.WHATSAPP_META_CLOUD);
        }
        if (gateway) {
            return Optional.of(RecursoFeature.WHATSAPP_GATEWAY);
        }
        if (recursoHabilitado(idOrganizacao, RecursoFeature.WHATSAPP)) {
            return Optional.of(RecursoFeature.WHATSAPP_GATEWAY);
        }
        return Optional.empty();
    }

    private void aplicarExclusividadeMotoresWhatsapp(Map<RecursoFeature, Boolean> estadoFinal) {
        boolean gateway = Boolean.TRUE.equals(estadoFinal.get(RecursoFeature.WHATSAPP_GATEWAY));
        boolean meta = Boolean.TRUE.equals(estadoFinal.get(RecursoFeature.WHATSAPP_META_CLOUD));

        if (gateway && meta) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Apenas um motor WhatsApp pode estar habilitado por organizacao (Gateway ou Meta Cloud).");
        }

        if (gateway || meta) {
            estadoFinal.put(RecursoFeature.WHATSAPP, false);
        }
    }

    private Map<RecursoFeature, Boolean> estadoAtual(Long idOrganizacao) {
        Map<RecursoFeature, Boolean> estado = new EnumMap<>(RecursoFeature.class);
        for (RecursoFeature recurso : RecursoFeature.values()) {
            estado.put(recurso, recursoHabilitado(idOrganizacao, recurso));
        }
        return estado;
    }

    private boolean recursoHabilitado(Long idOrganizacao, RecursoFeature recurso) {
        return repository.findByIdOrganizacaoAndRecurso(idOrganizacao, recurso)
                .map(OrganizacaoFeatureFlag::getHabilitado)
                .orElseGet(() -> padraoHabilitado(recurso));
    }

    private boolean padraoHabilitado(RecursoFeature recurso) {
        return switch (recurso) {
            case WHATSAPP_META_CLOUD, WHATSAPP, EMAIL, TELEGRAM, API_PUBLICA, ANALYTICS -> false;
            case WHATSAPP_GATEWAY, WEBHOOK, TEMPLATES -> true;
        };
    }

    private void salvarFlag(Long idOrganizacao, RecursoFeature recurso, boolean habilitado) {
        OrganizacaoFeatureFlag flag = repository.findByIdOrganizacaoAndRecurso(idOrganizacao, recurso)
                .orElseGet(() -> nova(idOrganizacao, recurso));
        flag.setHabilitado(habilitado);
        repository.save(flag);
    }

    private List<FeatureFlagResponse> listarOuPadrao(Long idOrganizacao) {
        Map<RecursoFeature, OrganizacaoFeatureFlag> existentes = repository.findByIdOrganizacaoOrderByRecursoAsc(idOrganizacao)
                .stream()
                .collect(java.util.stream.Collectors.toMap(OrganizacaoFeatureFlag::getRecurso, item -> item));

        return Arrays.stream(RecursoFeature.values())
                .map(recurso -> existentes.containsKey(recurso)
                        ? toResponse(existentes.get(recurso))
                        : new FeatureFlagResponse(null, idOrganizacao, recurso, padraoHabilitado(recurso)))
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
