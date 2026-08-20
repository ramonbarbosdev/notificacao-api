package com.notificacao_api.dto.notificacao;

import java.util.List;

public record EnviarNotificacaoLoteResposta(
        Boolean sucesso,
        int total,
        int aceitas,
        int rejeitadas,
        List<EnviarNotificacaoLoteItemResposta> itens) {
}
