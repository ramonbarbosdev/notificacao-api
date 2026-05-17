package com.notificacao_api.dto.template;

import java.util.List;

public record ValidarTemplateResponseDTO(
        boolean valido,
        List<String> variaveisEncontradas,
        List<String> variaveisDeclaradas,
        List<String> variaveisNaoDeclaradas,
        List<String> variaveisDeclaradasNaoUsadas,
        List<String> erros,
        List<String> avisos) {
}
