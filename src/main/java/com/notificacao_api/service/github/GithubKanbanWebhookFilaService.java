package com.notificacao_api.service.github;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.notificacao_api.dto.integracao.GithubKanbanMovimentacaoWebhookPayload;
import com.notificacao_api.model.github.GithubKanbanWebhookEventoPendente;
import com.notificacao_api.repository.github.GithubKanbanWebhookEventoPendenteRepository;

@Service
public class GithubKanbanWebhookFilaService {

    private final GithubKanbanWebhookEventoPendenteRepository repository;
    private final ObjectMapper objectMapper;

    public GithubKanbanWebhookFilaService(
            GithubKanbanWebhookEventoPendenteRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public boolean enfileirar(
            Long idOrganizacao,
            String deliveryId,
            String chaveDedup,
            GithubKanbanMovimentacaoWebhookPayload payload) {
        if (StringUtils.hasText(chaveDedup)
                && repository.existsByIdOrganizacaoAndDsChaveDedup(idOrganizacao, chaveDedup)) {
            return false;
        }
        if (StringUtils.hasText(deliveryId)
                && repository.existsByIdOrganizacaoAndDsDeliveryId(idOrganizacao, deliveryId)) {
            return false;
        }

        GithubKanbanWebhookEventoPendente row = new GithubKanbanWebhookEventoPendente();
        row.setIdOrganizacao(idOrganizacao);
        row.setDsDeliveryId(deliveryId);
        row.setDsChaveDedup(chaveDedup);
        row.setDsPayloadJson(serializar(payload));
        row.setDtOcorrido(LocalDateTime.now());
        repository.save(row);
        return true;
    }

    @Transactional(readOnly = true)
    public List<GithubKanbanWebhookEventoPendente> listarPendentes(Long idOrganizacao) {
        return repository.findByIdOrganizacaoOrderByDtOcorridoAsc(idOrganizacao);
    }

    @Transactional(readOnly = true)
    public List<Long> listarOrganizacoesComPendencias() {
        return repository.listarOrganizacoesComPendencias();
    }

    @Transactional
    public void removerEnviados(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        repository.deleteByIdIn(ids);
    }

    public GithubKanbanMovimentacaoWebhookPayload deserializar(String json) {
        try {
            return objectMapper.readValue(json, GithubKanbanMovimentacaoWebhookPayload.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Payload pendente invalido", ex);
        }
    }

    private String serializar(GithubKanbanMovimentacaoWebhookPayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Falha ao serializar payload kanban webhook", ex);
        }
    }
}
