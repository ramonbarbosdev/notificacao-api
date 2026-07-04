package com.notificacao_api.dto.alerta;

import java.time.LocalDateTime;

public record AlertaOperacionalResponse(
        Long idAlerta,
        Long idOrganizacao,
        Long idNotificacao,
        String tpOrigem,
        String dsTitulo,
        String dsMensagem,
        String dsDestinatario,
        String dsCanal,
        String dsCodigoErro,
        boolean flEmailEnviado,
        LocalDateTime dtCriacao) {
}
