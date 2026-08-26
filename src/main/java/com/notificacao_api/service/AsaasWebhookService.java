package com.notificacao_api.service;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.notificacao_api.integration.asaas.dto.AsaasWebhookPayload;
import com.notificacao_api.model.Organizacao;
import com.notificacao_api.model.OrganizacaoAssinatura;
import com.notificacao_api.model.PagamentoWebhookProcessado;
import com.notificacao_api.repository.OrganizacaoAssinaturaRepository;
import com.notificacao_api.repository.OrganizacaoRepository;
import com.notificacao_api.repository.PagamentoWebhookProcessadoRepository;

@Service
public class AsaasWebhookService {

    private final PagamentoWebhookProcessadoRepository webhookProcessadoRepository;
    private final OrganizacaoCobrancaService cobrancaService;
    private final AssinaturaService assinaturaService;
    private final OrganizacaoAssinaturaRepository assinaturaRepository;
    private final OrganizacaoRepository organizacaoRepository;

    public AsaasWebhookService(
            PagamentoWebhookProcessadoRepository webhookProcessadoRepository,
            OrganizacaoCobrancaService cobrancaService,
            AssinaturaService assinaturaService,
            OrganizacaoAssinaturaRepository assinaturaRepository,
            OrganizacaoRepository organizacaoRepository) {
        this.webhookProcessadoRepository = webhookProcessadoRepository;
        this.cobrancaService = cobrancaService;
        this.assinaturaService = assinaturaService;
        this.assinaturaRepository = assinaturaRepository;
        this.organizacaoRepository = organizacaoRepository;
    }

    @Transactional
    public void processar(AsaasWebhookPayload payload) {
        if (payload == null || !StringUtils.hasText(payload.event())) {
            return;
        }

        String idEvento = StringUtils.hasText(payload.id())
                ? payload.id()
                : payload.event() + "-" + (payload.payment() != null ? payload.payment().id() : System.currentTimeMillis());

        if (webhookProcessadoRepository.existsByIdEventoAsaas(idEvento)) {
            return;
        }

        switch (payload.event()) {
            case "PAYMENT_RECEIVED", "PAYMENT_CONFIRMED" -> processarPagamentoRecebido(payload);
            case "PAYMENT_OVERDUE" -> processarPagamentoVencido(payload);
            case "PAYMENT_DELETED", "PAYMENT_REFUNDED" -> processarPagamentoCancelado(payload);
            case "SUBSCRIPTION_DELETED" -> processarAssinaturaCancelada(payload);
            default -> {
            }
        }

        PagamentoWebhookProcessado processado = new PagamentoWebhookProcessado();
        processado.setIdEventoAsaas(idEvento);
        processado.setDsTipoEvento(payload.event());
        webhookProcessadoRepository.save(processado);
    }

    private void processarPagamentoRecebido(AsaasWebhookPayload payload) {
        if (payload.payment() == null) {
            return;
        }
        Optional<Long> idOrganizacao = resolverOrganizacao(payload);
        idOrganizacao.ifPresent(idOrg -> {
            cobrancaService.registrarOuAtualizar(idOrg, payload.payment());
            assinaturaRepository.findByIdOrganizacao(idOrg).ifPresent(assinatura -> {
                assinaturaService.ativarPorPagamento(idOrg, assinatura.getIdPlano());
            });
        });
    }

    private void processarPagamentoVencido(AsaasWebhookPayload payload) {
        if (payload.payment() == null) {
            return;
        }
        resolverOrganizacao(payload).ifPresent(idOrg -> {
            cobrancaService.registrarOuAtualizar(idOrg, payload.payment());
            assinaturaService.marcarInadimplente(idOrg);
        });
    }

    private void processarPagamentoCancelado(AsaasWebhookPayload payload) {
        if (payload.payment() == null) {
            return;
        }
        resolverOrganizacao(payload).ifPresent(idOrg -> cobrancaService.registrarOuAtualizar(idOrg, payload.payment()));
    }

    private void processarAssinaturaCancelada(AsaasWebhookPayload payload) {
        if (payload.subscription() == null || !StringUtils.hasText(payload.subscription().id())) {
            return;
        }
        assinaturaRepository.findByIdAssinaturaAsaas(payload.subscription().id())
                .ifPresent(assinatura -> assinaturaService.marcarInadimplente(assinatura.getIdOrganizacao()));
    }

    private Optional<Long> resolverOrganizacao(AsaasWebhookPayload payload) {
        if (payload.payment() != null && StringUtils.hasText(payload.payment().customer())) {
            Optional<Organizacao> porCliente = organizacaoRepository.findByIdClienteAsaas(payload.payment().customer());
            if (porCliente.isPresent()) {
                return Optional.of(porCliente.get().getIdOrganizacao());
            }
        }
        if (payload.payment() != null && StringUtils.hasText(payload.payment().subscription())) {
            return assinaturaRepository.findByIdAssinaturaAsaas(payload.payment().subscription())
                    .map(OrganizacaoAssinatura::getIdOrganizacao);
        }
        return Optional.empty();
    }
}
