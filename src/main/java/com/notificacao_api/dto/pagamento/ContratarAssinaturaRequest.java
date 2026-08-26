package com.notificacao_api.dto.pagamento;

import com.notificacao_api.enums.FormaPagamentoAssinatura;

import jakarta.validation.constraints.NotNull;

public record ContratarAssinaturaRequest(
        @NotNull Long idPlano,
        @NotNull FormaPagamentoAssinatura formaPagamento) {
}
