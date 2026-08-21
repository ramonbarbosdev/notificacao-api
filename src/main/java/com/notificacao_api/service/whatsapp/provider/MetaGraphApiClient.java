package com.notificacao_api.service.whatsapp.provider;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.notificacao_api.config.WhatsappMetaProperties;
import com.notificacao_api.service.whatsapp.WhatsappConfigurationService;
import com.notificacao_api.service.whatsapp.WhatsappConfigurationService.ConfiguracaoMetaAtiva;
import com.notificacao_api.service.whatsapp.WhatsappEnvioLogUtil;

@Service
public class MetaGraphApiClient {

    private static final Logger log = LoggerFactory.getLogger(MetaGraphApiClient.class);

    private final RestClient restClient;
    private final WhatsappMetaProperties properties;
    private final ObjectMapper objectMapper;

    public MetaGraphApiClient(
            RestClient.Builder builder,
            WhatsappMetaProperties properties,
            ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restClient = builder
                .baseUrl(properties.graphBaseUrl())
                .build();
    }

    public TesteConexaoResult testarConexao(ConfiguracaoMetaAtiva config) {
        try {
            JsonNode resposta = restClient.get()
                    .uri("/{apiVersion}/{phoneNumberId}", config.apiVersion(), config.phoneNumberId())
                    .header(HttpHeaders.AUTHORIZATION, bearer(config.accessToken()))
                    .retrieve()
                    .body(JsonNode.class);

            if (resposta != null && resposta.has("id")) {
                return TesteConexaoResult.sucesso("Configuracao validada com sucesso.");
            }
            return TesteConexaoResult.falha("Resposta inesperada da WhatsApp Cloud API.");
        } catch (HttpStatusCodeException ex) {
            return TesteConexaoResult.falha(mapearErroHttp(ex.getStatusCode().value(), ex.getResponseBodyAsString()));
        } catch (Exception ex) {
            log.warn("Falha ao testar Meta Cloud API: {}", ex.getMessage());
            return TesteConexaoResult.falha("Nao foi possivel autenticar na WhatsApp Cloud API.");
        }
    }

    public ResultadoEnvioWhatsapp enviarTexto(
            ConfiguracaoMetaAtiva config,
            String telefone,
            String mensagem) {
        Map<String, Object> payload = basePayload(telefone);
        payload.put("type", "text");
        payload.put("text", Map.of("body", mensagem));
        return enviar(config, payload, telefone);
    }

    public ResultadoEnvioWhatsapp enviarTemplate(
            ConfiguracaoMetaAtiva config,
            String telefone,
            String templateName,
            String language,
            Map<String, String> parameters) {
        Map<String, Object> template = new LinkedHashMap<>();
        template.put("name", templateName);
        template.put("language", Map.of("code", language));

        if (parameters != null && !parameters.isEmpty()) {
            List<Map<String, Object>> bodyParams = new ArrayList<>();
            for (String valor : parameters.values()) {
                bodyParams.add(Map.of("type", "text", "text", valor == null ? "" : valor));
            }
            template.put("components", List.of(Map.of("type", "body", "parameters", bodyParams)));
        }

        Map<String, Object> payload = basePayload(telefone);
        payload.put("type", "template");
        payload.put("template", template);
        return enviar(config, payload, telefone);
    }

    public ResultadoEnvioWhatsapp enviarImagem(
            ConfiguracaoMetaAtiva config,
            String telefone,
            String mediaUrl,
            String caption) {
        Map<String, Object> image = new LinkedHashMap<>();
        image.put("link", mediaUrl);
        if (caption != null && !caption.isBlank()) {
            image.put("caption", caption);
        }
        Map<String, Object> payload = basePayload(telefone);
        payload.put("type", "image");
        payload.put("image", image);
        return enviar(config, payload, telefone);
    }

    public ResultadoEnvioWhatsapp enviarDocumento(
            ConfiguracaoMetaAtiva config,
            String telefone,
            String mediaUrl,
            String filename) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("link", mediaUrl);
        if (filename != null && !filename.isBlank()) {
            document.put("filename", filename);
        }
        Map<String, Object> payload = basePayload(telefone);
        payload.put("type", "document");
        payload.put("document", document);
        return enviar(config, payload, telefone);
    }

    private ResultadoEnvioWhatsapp enviar(
            ConfiguracaoMetaAtiva config,
            Map<String, Object> payload,
            String telefone) {
        int tentativas = 0;
        int maxTentativas = 1 + Math.max(properties.maxRetriesTransient(), 0);

        while (tentativas < maxTentativas) {
            tentativas++;
            try {
                JsonNode resposta = restClient.post()
                        .uri("/{apiVersion}/{phoneNumberId}/messages", config.apiVersion(), config.phoneNumberId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(config.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(payload)
                        .retrieve()
                        .body(JsonNode.class);

                String messageId = extrairMessageId(resposta);
                if (messageId != null) {
                    log.info(
                            "Meta envio ok tenantId={} phone={} externalMessageId={}",
                            config.idOrganizacao(),
                            WhatsappEnvioLogUtil.mascararTelefone(telefone),
                            messageId);
                    return ResultadoEnvioWhatsapp.confirmado(messageId);
                }
                return ResultadoEnvioWhatsapp.falha("WhatsApp Cloud API nao retornou id da mensagem.");
            } catch (HttpStatusCodeException ex) {
                int status = ex.getStatusCode().value();
                String erro = mapearErroHttp(status, ex.getResponseBodyAsString());
                if (deveRetentar(status) && tentativas < maxTentativas) {
                    log.warn("Meta envio transient status={} tentativa={}/{}", status, tentativas, maxTentativas);
                    continue;
                }
                log.warn(
                        "Meta envio falhou tenantId={} phone={} httpStatus={} erro={}",
                        config.idOrganizacao(),
                        WhatsappEnvioLogUtil.mascararTelefone(telefone),
                        status,
                        erro);
                return ResultadoEnvioWhatsapp.falha(erro);
            } catch (Exception ex) {
                log.warn(
                        "Meta envio erro tenantId={} phone={} msg={}",
                        config.idOrganizacao(),
                        WhatsappEnvioLogUtil.mascararTelefone(telefone),
                        ex.getMessage());
                return ResultadoEnvioWhatsapp.falha("Falha ao enviar mensagem pela WhatsApp Cloud API.");
            }
        }
        return ResultadoEnvioWhatsapp.falha("Falha ao enviar mensagem pela WhatsApp Cloud API.");
    }

    private Map<String, Object> basePayload(String telefone) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("messaging_product", "whatsapp");
        payload.put("recipient_type", "individual");
        payload.put("to", telefone);
        return payload;
    }

    private String extrairMessageId(JsonNode resposta) {
        if (resposta == null) {
            return null;
        }
        JsonNode messages = resposta.get("messages");
        if (messages != null && messages.isArray() && !messages.isEmpty()) {
            JsonNode id = messages.get(0).get("id");
            return id != null ? id.asText() : null;
        }
        return null;
    }

    private boolean deveRetentar(int status) {
        return status == 429 || status >= 500;
    }

    private String mapearErroHttp(int status, String body) {
        String detalhe = extrairErroMeta(body);
        return switch (status) {
            case 400 -> "Requisicao invalida para WhatsApp Cloud API"
                    + (detalhe == null ? "." : ": " + detalhe);
            case 401 -> "Nao foi possivel autenticar na WhatsApp Cloud API. Verifique o access token.";
            case 403 -> "Acesso negado pela WhatsApp Cloud API. Verifique permissoes do token e WABA.";
            case 404 -> "Phone Number ID nao encontrado na WhatsApp Cloud API.";
            case 429 -> "Limite de requisicoes da WhatsApp Cloud API atingido. Tente novamente em instantes.";
            default -> status >= 500
                    ? "WhatsApp Cloud API indisponivel temporariamente."
                    : "Falha ao comunicar com WhatsApp Cloud API"
                            + (detalhe == null ? "." : ": " + detalhe);
        };
    }

    private String extrairErroMeta(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(body);
            JsonNode error = node.get("error");
            if (error != null && error.has("message")) {
                return error.get("message").asText();
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    public record TesteConexaoResult(boolean success, String message) {
        public static TesteConexaoResult sucesso(String message) {
            return new TesteConexaoResult(true, message);
        }

        public static TesteConexaoResult falha(String message) {
            return new TesteConexaoResult(false, message);
        }
    }
}
