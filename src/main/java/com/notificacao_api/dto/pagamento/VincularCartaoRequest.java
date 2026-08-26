package com.notificacao_api.dto.pagamento;

import jakarta.validation.constraints.NotBlank;

public record VincularCartaoRequest(
        @NotBlank String creditCardToken,
        String ultimos4Digitos,
        String bandeira,
        Boolean padrao) {
}
