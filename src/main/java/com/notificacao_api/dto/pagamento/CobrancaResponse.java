package com.notificacao_api.dto.pagamento;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.notificacao_api.enums.StatusCobranca;

public record CobrancaResponse(
        Long idCobranca,
        String idCobrancaAsaas,
        BigDecimal valor,
        StatusCobranca status,
        String pixCopiaCola,
        String pixQrBase64,
        LocalDate dtVencimento,
        LocalDateTime dtPagamento,
        LocalDateTime dtCriacao) {
}
