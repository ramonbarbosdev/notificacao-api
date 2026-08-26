package com.notificacao_api.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.notificacao_api.enums.StatusCobranca;
import com.notificacao_api.integration.asaas.dto.AsaasPaymentResponse;
import com.notificacao_api.model.OrganizacaoCobranca;
import com.notificacao_api.repository.OrganizacaoCobrancaRepository;

@Service
public class OrganizacaoCobrancaService {

    private static final DateTimeFormatter ASAAS_DATE = DateTimeFormatter.ISO_LOCAL_DATE;

    private final OrganizacaoCobrancaRepository cobrancaRepository;

    public OrganizacaoCobrancaService(OrganizacaoCobrancaRepository cobrancaRepository) {
        this.cobrancaRepository = cobrancaRepository;
    }

    @Transactional
    public OrganizacaoCobranca registrarOuAtualizar(Long idOrganizacao, AsaasPaymentResponse pagamento) {
        OrganizacaoCobranca cobranca = cobrancaRepository.findByIdCobrancaAsaas(pagamento.id())
                .orElseGet(() -> {
                    OrganizacaoCobranca nova = new OrganizacaoCobranca();
                    nova.setIdOrganizacao(idOrganizacao);
                    nova.setIdCobrancaAsaas(pagamento.id());
                    return nova;
                });

        cobranca.setVlCobranca(pagamento.value());
        cobranca.setStatus(mapearStatus(pagamento.status()));
        cobranca.setDsPixCopiaCola(pagamento.pixCopiaECola());
        cobranca.setDsPixQrBase64(pagamento.encodedImage());
        cobranca.setDtVencimento(parseDate(pagamento.dueDate()));
        if (cobranca.getStatus() == StatusCobranca.RECEBIDA) {
            cobranca.setDtPagamento(LocalDateTime.now());
        }
        return cobrancaRepository.save(cobranca);
    }

    private StatusCobranca mapearStatus(String statusAsaas) {
        if (!StringUtils.hasText(statusAsaas)) {
            return StatusCobranca.PENDENTE;
        }
        return switch (statusAsaas.toUpperCase()) {
            case "RECEIVED", "CONFIRMED" -> StatusCobranca.RECEBIDA;
            case "OVERDUE" -> StatusCobranca.VENCIDA;
            case "DELETED", "REFUNDED" -> StatusCobranca.CANCELADA;
            default -> StatusCobranca.PENDENTE;
        };
    }

    private LocalDate parseDate(String valor) {
        if (!StringUtils.hasText(valor)) {
            return null;
        }
        return LocalDate.parse(valor, ASAAS_DATE);
    }
}
