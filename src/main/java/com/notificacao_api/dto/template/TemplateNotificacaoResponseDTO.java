package com.notificacao_api.dto.template;

import java.time.LocalDateTime;
import java.util.Set;

import com.notificacao_api.enums.CanalNotificacao;

public record TemplateNotificacaoResponseDTO(
        Long idModelo,
        Long idOrganizacao,
        String nome,
        String chave,
        CanalNotificacao canal,
        String assunto,
        String conteudo,
        Boolean ativo,
        Set<String> variaveisObrigatorias,
        Integer versao,
        LocalDateTime dtCriacao,
        LocalDateTime dtAtualizacao) {
}
