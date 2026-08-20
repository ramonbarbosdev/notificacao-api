package com.notificacao_api.dto.notificacao;

import java.util.List;

import com.notificacao_api.enums.CanalNotificacao;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record EnviarNotificacaoLoteRequisicao(
        @NotNull CanalNotificacao canal,
        @NotEmpty List<@Valid EnviarNotificacaoLoteItemRequisicao> mensagens) {
}
