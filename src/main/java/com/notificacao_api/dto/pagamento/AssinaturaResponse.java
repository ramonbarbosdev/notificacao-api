package com.notificacao_api.dto.pagamento;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.notificacao_api.enums.FormaPagamentoAssinatura;
import com.notificacao_api.enums.StatusAssinatura;

public record AssinaturaResponse(
        Long idAssinatura,
        Long idPlano,
        String nmPlano,
        StatusAssinatura status,
        FormaPagamentoAssinatura formaPagamento,
        BigDecimal vlMensal,
        LocalDate dtProximoVencimento,
        LocalDateTime dtFimTrial) {
}
