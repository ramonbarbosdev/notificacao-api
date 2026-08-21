package com.notificacao_api.service.whatsapp.provider;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.notificacao_api.enums.WhatsappMensagemTipo;
import com.notificacao_api.enums.WhatsappProvedorEnvio;
import com.notificacao_api.model.Notificacao;
import com.notificacao_api.model.TemplateNotificacao;
import com.notificacao_api.repository.TemplateNotificacaoRepository;
import com.notificacao_api.service.whatsapp.WhatsappConfigurationService;
import com.notificacao_api.service.whatsapp.WhatsappMensagemService;

@Component
public class MetaCloudWhatsappProvider implements WhatsappEnvioProvider {

    private final WhatsappConfigurationService configurationService;
    private final MetaGraphApiClient metaGraphApiClient;
    private final WhatsappMensagemService mensagemService;
    private final TemplateNotificacaoRepository templateRepository;
    private final ObjectMapper objectMapper;

    public MetaCloudWhatsappProvider(
            WhatsappConfigurationService configurationService,
            MetaGraphApiClient metaGraphApiClient,
            WhatsappMensagemService mensagemService,
            TemplateNotificacaoRepository templateRepository,
            ObjectMapper objectMapper) {
        this.configurationService = configurationService;
        this.metaGraphApiClient = metaGraphApiClient;
        this.mensagemService = mensagemService;
        this.templateRepository = templateRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public WhatsappProvedorEnvio getProvedor() {
        return WhatsappProvedorEnvio.META_CLOUD;
    }

    @Override
    public ResultadoEnvioWhatsapp sendText(Long idOrganizacao, String phone, String message, Notificacao notificacao) {
        var config = configurationService.obterConfiguracaoMetaAtiva(idOrganizacao);
        ResultadoEnvioWhatsapp resultado = metaGraphApiClient.enviarTexto(config, phone, message);
        registrarEnvio(idOrganizacao, phone, notificacao, WhatsappMensagemTipo.TEXT, null, resultado);
        return resultado;
    }

    @Override
    public ResultadoEnvioWhatsapp sendTemplate(
            Long idOrganizacao,
            String phone,
            String templateName,
            String language,
            Map<String, String> parameters,
            Notificacao notificacao) {
        var config = configurationService.obterConfiguracaoMetaAtiva(idOrganizacao);
        ResultadoEnvioWhatsapp resultado =
                metaGraphApiClient.enviarTemplate(config, phone, templateName, language, parameters);
        registrarEnvio(idOrganizacao, phone, notificacao, WhatsappMensagemTipo.TEMPLATE, templateName, resultado);
        return resultado;
    }

    @Override
    public ResultadoEnvioWhatsapp sendImage(
            Long idOrganizacao, String phone, String mediaUrl, String caption, Notificacao notificacao) {
        var config = configurationService.obterConfiguracaoMetaAtiva(idOrganizacao);
        ResultadoEnvioWhatsapp resultado = metaGraphApiClient.enviarImagem(config, phone, mediaUrl, caption);
        registrarEnvio(idOrganizacao, phone, notificacao, WhatsappMensagemTipo.IMAGE, null, resultado);
        return resultado;
    }

    @Override
    public ResultadoEnvioWhatsapp sendDocument(
            Long idOrganizacao, String phone, String mediaUrl, String filename, Notificacao notificacao) {
        var config = configurationService.obterConfiguracaoMetaAtiva(idOrganizacao);
        ResultadoEnvioWhatsapp resultado = metaGraphApiClient.enviarDocumento(config, phone, mediaUrl, filename);
        registrarEnvio(idOrganizacao, phone, notificacao, WhatsappMensagemTipo.DOCUMENT, null, resultado);
        return resultado;
    }

    public ResultadoEnvioWhatsapp enviarNotificacao(Long idOrganizacao, Notificacao notificacao) {
        String phone = notificacao.getDestinatario();

        if (notificacao.getChaveModelo() != null && !notificacao.getChaveModelo().isBlank()) {
            TemplateNotificacao template = templateRepository
                    .findByIdOrganizacaoAndChave(idOrganizacao, notificacao.getChaveModelo())
                    .orElse(null);

            if (template != null
                    && template.getMetaTemplateName() != null
                    && !template.getMetaTemplateName().isBlank()) {
                String language = template.getMetaIdioma() != null && !template.getMetaIdioma().isBlank()
                        ? template.getMetaIdioma()
                        : "pt_BR";
                Map<String, String> parametros = parseVariaveis(notificacao.getVariaveisTemplate());
                return sendTemplate(
                        idOrganizacao,
                        phone,
                        template.getMetaTemplateName(),
                        language,
                        parametros,
                        notificacao);
            }
        }

        return sendText(idOrganizacao, phone, notificacao.getMensagem(), notificacao);
    }

    private Map<String, String> parseVariaveis(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, String>>() {});
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private void registrarEnvio(
            Long idOrganizacao,
            String phone,
            Notificacao notificacao,
            WhatsappMensagemTipo tipo,
            String templateName,
            ResultadoEnvioWhatsapp resultado) {
        mensagemService.registrarEnvioOutbound(
                idOrganizacao,
                notificacao != null ? notificacao.getIdNotificacao() : null,
                phone,
                tipo,
                templateName,
                resultado);
    }
}
