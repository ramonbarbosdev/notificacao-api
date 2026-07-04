package com.notificacao_api.service.whatsapp;

import com.notificacao_api.dto.whatsapp.EnviarMensagemWhatsappRequisicao;
import com.notificacao_api.dto.whatsapp.EnviarMensagemWhatsappResposta;
import com.notificacao_api.dto.whatsapp.StatusWhatsappResposta;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Service
public class WhatsAppGatewayClient {

    private final RestClient restClient;

    public WhatsAppGatewayClient(
            RestClient.Builder builder,
            @Value("${whatsapp.gateway.base-url}") String baseUrl,
            @Value("${whatsapp.gateway.api-key}") String apiKey) {
        this.restClient = builder
                .baseUrl(baseUrl)
                .defaultHeader("X-API-KEY", apiKey)
                .build();
    }

    public StatusWhatsappResposta conectar(Long idOrganizacao) {
        try {
            StatusWhatsappResposta resposta = restClient.post()
                    .uri("/sessoes/{idOrganizacao}/conectar", idOrganizacao)
                    .retrieve()
                    .body(StatusWhatsappResposta.class);
            return normalizarResposta(idOrganizacao, resposta, "conectar sessao WhatsApp");
        } catch (Exception ex) {
            return respostaErro(idOrganizacao, ex);
        }
    }

    public StatusWhatsappResposta obterStatus(Long idOrganizacao) {
        try {
            StatusWhatsappResposta resposta = restClient.get()
                    .uri("/sessoes/{idOrganizacao}/status", idOrganizacao)
                    .retrieve()
                    .body(StatusWhatsappResposta.class);
            return normalizarResposta(idOrganizacao, resposta, "consultar status WhatsApp");
        } catch (Exception ex) {
            return respostaErro(idOrganizacao, ex);
        }
    }

    public EnviarMensagemWhatsappResposta enviarMensagem(
            Long idOrganizacao,
            EnviarMensagemWhatsappRequisicao requisicao) {
        try {
            EnviarMensagemWhatsappResposta resposta = restClient.post()
                    .uri("/sessoes/{idOrganizacao}/enviar-mensagem", idOrganizacao)
                    .body(requisicao)
                    .retrieve()
                    .body(EnviarMensagemWhatsappResposta.class);
            if (resposta != null) {
                return resposta;
            }
            return new EnviarMensagemWhatsappResposta(
                    false, idOrganizacao, null, requisicao.telefone(), null,
                    "Gateway WhatsApp nao retornou resposta ao enviar mensagem.");
        } catch (HttpStatusCodeException ex) {
            String respostaErro = ex.getResponseBodyAsString();
            return new EnviarMensagemWhatsappResposta(
                    false,
                    idOrganizacao,
                    null,
                    requisicao.telefone(),
                    null,
                    extrairMensagemErroEnvio(respostaErro, ex));
        } catch (Exception ex) {
            return new EnviarMensagemWhatsappResposta(
                    false,
                    idOrganizacao,
                    null,
                    requisicao.telefone(),
                    null,
                    WhatsappGatewayErroUtil.mensagemParaUsuario(ex));
        }
    }

    public StatusWhatsappResposta desconectar(Long idOrganizacao) {
        try {
            StatusWhatsappResposta resposta = restClient.post()
                    .uri("/sessoes/{idOrganizacao}/desconectar", idOrganizacao)
                    .retrieve()
                    .body(StatusWhatsappResposta.class);
            return normalizarResposta(idOrganizacao, resposta, "desconectar sessao WhatsApp");
        } catch (Exception ex) {
            return respostaErro(idOrganizacao, ex);
        }
    }

    private StatusWhatsappResposta normalizarResposta(
            Long idOrganizacao,
            StatusWhatsappResposta resposta,
            String acao) {
        if (resposta == null) {
            return respostaErro(idOrganizacao,
                    "Gateway WhatsApp nao respondeu ao " + acao + ".");
        }
        if (Boolean.FALSE.equals(resposta.sucesso()) && resposta.erro() != null && !resposta.erro().isBlank()) {
            return new StatusWhatsappResposta(
                    false,
                    resposta.idOrganizacao() != null ? resposta.idOrganizacao() : idOrganizacao,
                    resposta.status() != null ? resposta.status() : "ERRO",
                    false,
                    resposta.qr(),
                    resposta.qrImagem(),
                    resposta.telefone(),
                    WhatsappGatewayErroUtil.mensagemTextoGateway(resposta.erro()));
        }
        return resposta;
    }

    private StatusWhatsappResposta respostaErro(Long idOrganizacao, Throwable ex) {
        return new StatusWhatsappResposta(
                false,
                idOrganizacao,
                "ERRO",
                false,
                null,
                null,
                null,
                WhatsappGatewayErroUtil.mensagemParaUsuario(ex));
    }

    private StatusWhatsappResposta respostaErro(Long idOrganizacao, String mensagem) {
        return new StatusWhatsappResposta(
                false, idOrganizacao, "ERRO", false, null, null, null, mensagem);
    }

    private String extrairMensagemErroEnvio(String responseBody, HttpStatusCodeException ex) {
        if (ex.getStatusCode().value() >= 502 && ex.getStatusCode().value() <= 504) {
            return "O gateway WhatsApp esta temporariamente indisponivel.";
        }
        return extrairMensagemErroLegado(responseBody);
    }

    private String extrairMensagemErroLegado(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return WhatsappGatewayErroUtil.mensagemParaUsuario(
                    new RestClientResponseException("Gateway", 502, "Bad Gateway", null, null, null));
        }

        if (responseBody.contains("Sessão não iniciada")) {
            return "Sessao do WhatsApp nao iniciada para esta organizacao.";
        }

        if (responseBody.contains("WhatsApp não conectado")) {
            return "WhatsApp nao conectado para esta organizacao.";
        }

        if (responseBody.contains("Número não encontrado")) {
            return "Numero informado nao encontrado no WhatsApp.";
        }

        return WhatsappGatewayErroUtil.mensagemParaUsuario(
                new RuntimeException(responseBody));
    }
}
