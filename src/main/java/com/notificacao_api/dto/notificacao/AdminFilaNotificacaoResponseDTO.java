package com.notificacao_api.dto.notificacao;

import java.time.LocalDateTime;

import com.notificacao_api.enums.CanalNotificacao;
import com.notificacao_api.enums.StatusNotificacao;

public record AdminFilaNotificacaoResponseDTO(
        Long idNotificacao,
        Long idOrganizacao,
        String nmOrganizacao,
        CanalNotificacao canal,
        String destinatario,
        String assunto,
        String mensagemResumo,
        StatusNotificacao status,
        String provider,
        Integer tentativas,
        Integer tentativasMaximas,
        LocalDateTime proximaTentativa,
        LocalDateTime enviadoEm,
        LocalDateTime retomadaPrevistaEm,
        String retomadaPrevistaTexto,
        String erro,
        String motivoAguardando,
        String codigoErro,
        LocalDateTime criadoEm,
        LocalDateTime atualizadoEm,
        String acaoSugeridaCodigo,
        String acaoSugeridaTitulo,
        String acaoSugeridaDetalhe,
        boolean acaoSugeridaDestaque) {
}
