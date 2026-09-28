package com.notificacao_api.dto.configuracao;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record TcTokenConfirmacaoAutomaticaDiasAntesRequest(
        @NotNull @Min(1) @Max(28) Integer diasAntesExpirar) {}
