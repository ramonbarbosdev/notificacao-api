package com.notificacao_api.dto.notificacao;

import java.time.LocalDateTime;

import com.notificacao_api.enums.StatusOperacionalSessao;

public record AdminOrganizacaoOperacionalResumoDTO(
        Long idOrganizacao,
        String nmOrganizacao,
        StatusOperacionalSessao statusOperacionalWhatsapp,
        boolean precisaReativar,
        boolean podeCancelarPausa,
        LocalDateTime pausadoAte,
        String pausadoAteTexto,
        long pendentes,
        long processando,
        long falhasContatoWhatsapp) {
}
