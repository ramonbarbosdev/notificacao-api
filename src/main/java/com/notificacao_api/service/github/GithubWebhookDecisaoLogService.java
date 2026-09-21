package com.notificacao_api.service.github;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.notificacao_api.dto.integracao.GithubWebhookDecisaoListaResponse;
import com.notificacao_api.dto.integracao.GithubWebhookDecisaoResponse;
import com.notificacao_api.model.GithubWebhookDecisaoLog;
import com.notificacao_api.repository.GithubWebhookDecisaoLogRepository;
import com.notificacao_api.service.TenantContextService;

@Service
public class GithubWebhookDecisaoLogService {

    public static final String RESULTADO_PING = "PING";
    public static final String RESULTADO_IGNORADO_EVENTO = "IGNORADO_EVENTO";
    public static final String RESULTADO_IGNORADO_GATILHO = "IGNORADO_GATILHO";
    public static final String RESULTADO_IGNORADO_STATUS = "IGNORADO_STATUS";
    public static final String RESULTADO_IGNORADO_SEM_OPTIN = "IGNORADO_SEM_OPTIN";
    public static final String RESULTADO_FILA_SEM_DESTINATARIO = "FILA_SEM_DESTINATARIO";
    public static final String RESULTADO_ENVIADO = "ENVIADO";

    private final GithubWebhookDecisaoLogRepository repository;
    private final TenantContextService tenantContextService;
    private final ObjectMapper objectMapper;

    public GithubWebhookDecisaoLogService(
            GithubWebhookDecisaoLogRepository repository,
            TenantContextService tenantContextService,
            ObjectMapper objectMapper) {
        this.repository = repository;
        this.tenantContextService = tenantContextService;
        this.objectMapper = objectMapper;
    }

    public record RegistrarDecisaoParams(
            Long idOrganizacao,
            String deliveryId,
            String githubEvent,
            String action,
            String resultado,
            String descricao,
            String tituloCard,
            String statusDestino,
            String statusAnterior,
            boolean pullRequest,
            boolean issueProjectV2,
            String fluxoDestinatarios,
            List<String> loginsDestino,
            int whatsappEnfileirados,
            Map<String, Object> detalhe) {
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrar(RegistrarDecisaoParams params) {
        GithubWebhookDecisaoLog log = new GithubWebhookDecisaoLog();
        log.setIdOrganizacao(params.idOrganizacao());
        log.setDsDeliveryId(truncar(params.deliveryId(), 120));
        log.setDsGithubEvent(truncar(params.githubEvent(), 80));
        log.setDsAction(truncar(params.action(), 80));
        log.setDsResultado(params.resultado());
        log.setDsDescricao(params.descricao());
        log.setDsTituloCard(truncar(params.tituloCard(), 500));
        log.setDsStatusDestino(truncar(params.statusDestino(), 200));
        log.setDsStatusAnterior(truncar(params.statusAnterior(), 200));
        log.setFlPullRequest(params.pullRequest());
        log.setFlIssueProjectV2(params.issueProjectV2());
        log.setDsFluxoDestinatarios(truncar(params.fluxoDestinatarios(), 40));
        if (params.loginsDestino() != null && !params.loginsDestino().isEmpty()) {
            log.setDsLoginsDestino(String.join(", ", params.loginsDestino()));
        }
        log.setNuWhatsappEnfileirados(params.whatsappEnfileirados());
        log.setDsDetalheJson(serializarDetalhe(params.detalhe()));
        repository.save(log);
    }

    @Transactional(readOnly = true)
    public GithubWebhookDecisaoListaResponse listarOrganizacaoAtual(int pagina, int tamanho) {
        Long idOrganizacao = tenantContextService.idOrganizacaoObrigatoria();
        int size = Math.min(Math.max(tamanho, 1), 50);
        int page = Math.max(pagina, 0);
        Pageable pageable = PageRequest.of(page, size);
        Page<GithubWebhookDecisaoLog> resultado =
                repository.findByIdOrganizacaoOrderByDtCriacaoDesc(idOrganizacao, pageable);
        List<GithubWebhookDecisaoResponse> itens =
                resultado.getContent().stream().map(this::paraResponse).toList();
        return new GithubWebhookDecisaoListaResponse(
                itens,
                resultado.getNumber(),
                resultado.getSize(),
                resultado.getTotalElements(),
                resultado.getTotalPages());
    }

    private GithubWebhookDecisaoResponse paraResponse(GithubWebhookDecisaoLog entity) {
        return new GithubWebhookDecisaoResponse(
                entity.getIdGithubWebhookDecisaoLog(),
                entity.getDsDeliveryId(),
                entity.getDsGithubEvent(),
                entity.getDsAction(),
                entity.getDsResultado(),
                entity.getDsDescricao(),
                entity.getDsTituloCard(),
                entity.getDsStatusDestino(),
                entity.getDsStatusAnterior(),
                entity.isFlPullRequest(),
                entity.isFlIssueProjectV2(),
                entity.getDsFluxoDestinatarios(),
                entity.getDsLoginsDestino(),
                entity.getNuWhatsappEnfileirados(),
                deserializarDetalhe(entity.getDsDetalheJson()),
                entity.getDtCriacao());
    }

    private String serializarDetalhe(Map<String, Object> detalhe) {
        if (detalhe == null || detalhe.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(detalhe);
        } catch (JsonProcessingException ex) {
            return null;
        }
    }

    private Map<String, Object> deserializarDetalhe(String json) {
        if (!StringUtils.hasText(json)) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException ex) {
            return Collections.emptyMap();
        }
    }

    private static String truncar(String valor, int max) {
        if (!StringUtils.hasText(valor)) {
            return null;
        }
        String trimmed = valor.trim();
        if (trimmed.length() <= max) {
            return trimmed;
        }
        return trimmed.substring(0, max);
    }
}
