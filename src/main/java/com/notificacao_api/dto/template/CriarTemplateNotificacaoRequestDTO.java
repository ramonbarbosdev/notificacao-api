package com.notificacao_api.dto.template;

import java.util.Set;

import com.notificacao_api.enums.CanalNotificacao;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CriarTemplateNotificacaoRequestDTO(
        @NotBlank String nome,
        @NotBlank String chave,
        @NotNull CanalNotificacao canal,
        String assunto,
        @NotBlank String conteudo,
        Boolean ativo,
        Set<String> variaveisObrigatorias) {
}
