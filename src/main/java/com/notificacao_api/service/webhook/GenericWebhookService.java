package com.notificacao_api.service.webhook;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.notificacao_api.dto.integracao.WebhookGenericoRequest;
import com.notificacao_api.dto.notificacao.EnviarNotificacaoRequisicao;
import com.notificacao_api.dto.notificacao.EnviarNotificacaoResposta;
import com.notificacao_api.enums.CanalNotificacao;
import com.notificacao_api.enums.RecursoFeature;
import com.notificacao_api.model.OrganizacaoConfiguracao;
import com.notificacao_api.repository.OrganizacaoConfiguracaoRepository;
import com.notificacao_api.service.FeatureFlagService;
import com.notificacao_api.service.NotificacaoService;
import com.notificacao_api.service.OrganizacaoConfiguracaoService;

@Service
public class GenericWebhookService {

    private static final Logger log = LoggerFactory.getLogger(GenericWebhookService.class);
    private static final String PLACEHOLDER_SEM_DESTINATARIO = "webhook:sem-destinatario";

    private final FeatureFlagService featureFlagService;
    private final OrganizacaoConfiguracaoRepository configuracaoRepository;
    private final OrganizacaoConfiguracaoService organizacaoConfiguracaoService;
    private final ObjectMapper objectMapper;
    private final NotificacaoService notificacaoService;

    public GenericWebhookService(
            FeatureFlagService featureFlagService,
            OrganizacaoConfiguracaoRepository configuracaoRepository,
            OrganizacaoConfiguracaoService organizacaoConfiguracaoService,
            ObjectMapper objectMapper,
            NotificacaoService notificacaoService) {
        this.featureFlagService = featureFlagService;
        this.configuracaoRepository = configuracaoRepository;
        this.organizacaoConfiguracaoService = organizacaoConfiguracaoService;
        this.objectMapper = objectMapper;
        this.notificacaoService = notificacaoService;
    }

    public EnviarNotificacaoResposta processar(
            Long idOrganizacao,
            String contentType,
            String deliveryId,
            String payloadJsonOuTexto) {

        featureFlagService.validarRecursoHabilitado(idOrganizacao, RecursoFeature.WEBHOOK_GENERICO);

        OrganizacaoConfiguracao configuracao = configuracaoRepository.findByIdOrganizacao(idOrganizacao)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT, "Configuracao da organizacao nao encontrada."));

        WebhookGenericoRequest dados = parsePayload(contentType, payloadJsonOuTexto);
        CanalNotificacao canal = dados.canal() != null ? dados.canal() : CanalNotificacao.WHATSAPP;
        if (canal != CanalNotificacao.WHATSAPP) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Webhook generico suporta apenas canal WHATSAPP no momento.");
        }

        String assunto = primeiroTexto(dados.assunto(), dados.titulo());
        String referencia = primeiroTexto(dados.referenciaExterna(), deliveryId != null ? "webhook:" + deliveryId : null);

        EnviarNotificacaoRequisicao requisicao = new EnviarNotificacaoRequisicao(
                canal,
                "",
                assunto,
                dados.mensagem().trim(),
                null,
                null,
                referencia);

        String destinatario = dados.destinatario() != null ? dados.destinatario().trim() : "";
        if (!StringUtils.hasText(destinatario)) {
            if (!organizacaoConfiguracaoService.deveRegistrarFilaSemDestinatario(configuracao)) {
                log.info(
                        "Webhook generico ignorado sem destinatario org={} delivery={} referencia={}",
                        idOrganizacao,
                        deliveryId,
                        referencia);
                return new EnviarNotificacaoResposta(
                        false,
                        null,
                        canal,
                        null,
                        "Ignorado: sem destinatario e registro na fila desabilitado na organizacao.",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null);
            }
            log.info(
                    "Webhook generico sem destinatario org={} delivery={} referencia={}",
                    idOrganizacao,
                    deliveryId,
                    referencia);
            return notificacaoService.enfileirarSomenteRegistroFila(
                    idOrganizacao,
                    requisicao,
                    PLACEHOLDER_SEM_DESTINATARIO,
                    "Evento webhook generico registrado na fila sem destinatario WhatsApp.");
        }

        EnviarNotificacaoRequisicao comDestino = new EnviarNotificacaoRequisicao(
                canal,
                destinatario,
                assunto,
                dados.mensagem().trim(),
                null,
                null,
                referencia);

        try {
            EnviarNotificacaoResposta resposta = notificacaoService.enviarParaOrganizacao(idOrganizacao, comDestino);
            log.info(
                    "Webhook generico enfileirado org={} delivery={} idNotificacao={}",
                    idOrganizacao,
                    deliveryId,
                    resposta.idNotificacao());
            return resposta;
        } catch (ResponseStatusException ex) {
            log.warn(
                    "Webhook generico falhou ao enfileirar org={} delivery={} status={} motivo={}",
                    idOrganizacao,
                    deliveryId,
                    ex.getStatusCode(),
                    ex.getReason());
            throw ex;
        }
    }

    private WebhookGenericoRequest parsePayload(String contentType, String body) {
        if (!StringUtils.hasText(body)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Corpo do webhook vazio.");
        }

        boolean json = contentType != null
                && (contentType.toLowerCase().contains("application/json")
                        || contentType.toLowerCase().contains("+json"));

        if (!json) {
            return new WebhookGenericoRequest(null, null, null, null, body.trim(), null);
        }

        try {
            JsonNode root = objectMapper.readTree(body);
            if (root.isTextual()) {
                return new WebhookGenericoRequest(null, null, null, null, root.asText(), null);
            }

            WebhookGenericoRequest dto = objectMapper.treeToValue(root, WebhookGenericoRequest.class);
            if (dto == null || !StringUtils.hasText(dto.mensagem())) {
                String mensagem = texto(root, "mensagem");
                if (!StringUtils.hasText(mensagem)) {
                    mensagem = texto(root, "message");
                }
                if (!StringUtils.hasText(mensagem)) {
                    mensagem = texto(root, "text");
                }
                if (!StringUtils.hasText(mensagem)) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "Informe o campo mensagem (ou message/text) no JSON.");
                }
                CanalNotificacao canal = canalDe(root);
                return new WebhookGenericoRequest(
                        canal,
                        texto(root, "destinatario"),
                        texto(root, "assunto"),
                        texto(root, "titulo"),
                        mensagem,
                        texto(root, "referenciaExterna"));
            }
            return dto;
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "JSON do webhook invalido.");
        }
    }

    private CanalNotificacao canalDe(JsonNode root) {
        String canal = texto(root, "canal");
        if (!StringUtils.hasText(canal)) {
            return null;
        }
        try {
            return CanalNotificacao.valueOf(canal.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Canal invalido: " + canal);
        }
    }

    private String texto(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        return value.asText(null);
    }

    private String primeiroTexto(String... valores) {
        if (valores == null) {
            return null;
        }
        for (String valor : valores) {
            if (StringUtils.hasText(valor)) {
                return valor.trim();
            }
        }
        return null;
    }
}
