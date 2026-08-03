package com.notificacao_api.dto.notificacao;

import java.time.LocalDateTime;

import com.notificacao_api.dto.whatsapp.SessaoOperacionalContextoDTO;
import com.notificacao_api.enums.CanalNotificacao;

public record StatusEnvioOrganizacaoResponse(
        boolean podeEnviar,
        CanalNotificacao canal,
        LocalDateTime retomadaPrevistaEm,
        String retomadaPrevistaTexto,
        String titulo,
        String motivo,
        String orientacao,
        SessaoOperacionalContextoDTO operacionalWhatsapp) {

    public static StatusEnvioOrganizacaoResponse liberado(CanalNotificacao canal) {
        return new StatusEnvioOrganizacaoResponse(
                true,
                canal,
                null,
                null,
                "Envios liberados",
                null,
                "Novas mensagens podem ser enfileiradas normalmente.",
                null);
    }
}
