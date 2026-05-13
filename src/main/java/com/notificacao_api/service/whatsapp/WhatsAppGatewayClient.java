package com.notificacao_api.service.whatsapp;

import com.notificacao_api.dto.whatsapp.EnviarMensagemWhatsappRequisicao;
import com.notificacao_api.dto.whatsapp.EnviarMensagemWhatsappResposta;
import com.notificacao_api.dto.whatsapp.StatusWhatsappResposta;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

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
            return restClient.post()
                    .uri("/sessoes/{idOrganizacao}/conectar", idOrganizacao)
                    .retrieve()
                    .body(StatusWhatsappResposta.class);
        } catch (RestClientResponseException ex) {
            throw erroGateway("conectar sessao WhatsApp", ex);
        }
    }

    public StatusWhatsappResposta obterStatus(Long idOrganizacao) {
        try {
            return restClient.get()
                    .uri("/sessoes/{idOrganizacao}/status", idOrganizacao)
                    .retrieve()
                    .body(StatusWhatsappResposta.class);
        } catch (RestClientResponseException ex) {
            throw erroGateway("consultar status WhatsApp", ex);
        }
    }

    public EnviarMensagemWhatsappResposta enviarMensagem(
            Long idOrganizacao,
            EnviarMensagemWhatsappRequisicao requisicao) {
        try {

            return restClient.post()
                    .uri("/sessoes/{idOrganizacao}/enviar-mensagem", idOrganizacao)
                    .body(requisicao)
                    .retrieve()
                    .body(EnviarMensagemWhatsappResposta.class);

        } catch (HttpStatusCodeException ex) {

            String respostaErro = ex.getResponseBodyAsString();

            return new EnviarMensagemWhatsappResposta(
                    false,
                    idOrganizacao,
                    null,
                    requisicao.telefone(),
                    null,
                    extrairMensagemErro(respostaErro));

        } catch (Exception ex) {

            return new EnviarMensagemWhatsappResposta(
                    false,
                    idOrganizacao,
                    null,
                    requisicao.telefone(),
                    null,
                    "Falha ao comunicar com gateway WhatsApp.");
        }
    }

    public StatusWhatsappResposta desconectar(Long idOrganizacao) {
        try {
            return restClient.post()
                    .uri("/sessoes/{idOrganizacao}/desconectar", idOrganizacao)
                    .retrieve()
                    .body(StatusWhatsappResposta.class);
        } catch (RestClientResponseException ex) {
            throw erroGateway("desconectar sessao WhatsApp", ex);
        }
    }

    private ResponseStatusException erroGateway(String acao, RestClientResponseException ex) {
        String detalhe = ex.getResponseBodyAsString();
        String mensagem = detalhe == null || detalhe.isBlank()
                ? "Falha ao " + acao + " no gateway"
                : "Falha ao " + acao + " no gateway: " + detalhe;

        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        return new ResponseStatusException(
                status == null ? HttpStatus.BAD_GATEWAY : status,
                mensagem,
                ex);
    }

    private String extrairMensagemErro(String responseBody) {

        if (responseBody == null || responseBody.isBlank()) {
            return "Erro desconhecido no gateway WhatsApp.";
        }

        if (responseBody.contains("Sessão não iniciada")) {
            return "Sessão do WhatsApp não iniciada para esta organização.";
        }

        if (responseBody.contains("WhatsApp não conectado")) {
            return "WhatsApp não conectado para esta organização.";
        }

        if (responseBody.contains("Número não encontrado")) {
            return "Número informado não encontrado no WhatsApp.";
        }

        return "Falha ao enviar mensagem pelo WhatsApp.";
    }
}
