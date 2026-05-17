package com.notificacao_api.dto.configuracao;

import java.time.LocalDateTime;

public record AuditoriaEventoResponse(
        Long idAuditoria,
        Long idOrganizacao,
        Long idUsuario,
        String role,
        String modulo,
        String acao,
        String descricao,
        String ip,
        String userAgent,
        String dadosAntes,
        String dadosDepois,
        LocalDateTime dtCriacao) {
}
