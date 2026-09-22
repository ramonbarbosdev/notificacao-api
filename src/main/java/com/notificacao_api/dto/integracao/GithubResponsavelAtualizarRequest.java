package com.notificacao_api.dto.integracao;

import jakarta.validation.constraints.NotNull;

public record GithubResponsavelAtualizarRequest(@NotNull Boolean ativo) {}
