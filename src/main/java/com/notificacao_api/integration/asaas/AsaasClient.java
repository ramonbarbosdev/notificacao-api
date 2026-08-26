package com.notificacao_api.integration.asaas;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import com.notificacao_api.config.AsaasProperties;
import com.notificacao_api.integration.asaas.dto.AsaasCustomerRequest;
import com.notificacao_api.integration.asaas.dto.AsaasCustomerResponse;
import com.notificacao_api.integration.asaas.dto.AsaasPaymentListResponse;
import com.notificacao_api.integration.asaas.dto.AsaasPaymentResponse;
import com.notificacao_api.integration.asaas.dto.AsaasSubscriptionRequest;
import com.notificacao_api.integration.asaas.dto.AsaasSubscriptionResponse;

@Service
public class AsaasClient {

    private final RestClient restClient;
    private final AsaasProperties properties;

    public AsaasClient(RestClient.Builder builder, AsaasProperties properties) {
        this.properties = properties;
        this.restClient = builder
                .baseUrl(properties.baseUrl())
                .defaultHeader("access_token", properties.apiKey())
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    public void validarConfiguracao() {
        if (!StringUtils.hasText(properties.apiKey())) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Integracao Asaas nao configurada. Defina ASAAS_API_KEY.");
        }
    }

    public AsaasCustomerResponse criarCliente(AsaasCustomerRequest request) {
        validarConfiguracao();
        try {
            return restClient.post()
                    .uri("/customers")
                    .body(request)
                    .retrieve()
                    .body(AsaasCustomerResponse.class);
        } catch (RestClientResponseException ex) {
            throw traduzirErro("criar cliente Asaas", ex);
        }
    }

    public AsaasSubscriptionResponse criarAssinatura(AsaasSubscriptionRequest request) {
        validarConfiguracao();
        try {
            return restClient.post()
                    .uri("/subscriptions")
                    .body(request)
                    .retrieve()
                    .body(AsaasSubscriptionResponse.class);
        } catch (RestClientResponseException ex) {
            throw traduzirErro("criar assinatura Asaas", ex);
        }
    }

    public void cancelarAssinatura(String idAssinaturaAsaas) {
        validarConfiguracao();
        try {
            restClient.delete()
                    .uri("/subscriptions/{id}", idAssinaturaAsaas)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException ex) {
            throw traduzirErro("cancelar assinatura Asaas", ex);
        }
    }

    public AsaasPaymentResponse buscarPagamento(String idPagamentoAsaas) {
        validarConfiguracao();
        try {
            return restClient.get()
                    .uri("/payments/{id}", idPagamentoAsaas)
                    .retrieve()
                    .body(AsaasPaymentResponse.class);
        } catch (RestClientResponseException ex) {
            throw traduzirErro("buscar pagamento Asaas", ex);
        }
    }

    public AsaasPaymentListResponse listarPagamentosAssinatura(String idAssinaturaAsaas) {
        validarConfiguracao();
        try {
            return restClient.get()
                    .uri("/subscriptions/{id}/payments", idAssinaturaAsaas)
                    .retrieve()
                    .body(AsaasPaymentListResponse.class);
        } catch (RestClientResponseException ex) {
            throw traduzirErro("listar pagamentos da assinatura Asaas", ex);
        }
    }

    public boolean webhookTokenValido(String tokenRecebido) {
        if (!StringUtils.hasText(properties.webhookAccessToken())) {
            return true;
        }
        return properties.webhookAccessToken().equals(tokenRecebido);
    }

    private ResponseStatusException traduzirErro(String operacao, RestClientResponseException ex) {
        String corpo = ex.getResponseBodyAsString();
        String mensagem = StringUtils.hasText(corpo)
                ? operacao + ": " + corpo
                : operacao + ": " + ex.getMessage();
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, mensagem);
    }
}
