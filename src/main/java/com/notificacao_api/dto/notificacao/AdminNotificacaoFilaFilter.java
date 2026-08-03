package com.notificacao_api.dto.notificacao;

import com.notificacao_api.shared.FilterEquals;
import com.notificacao_api.shared.FilterLike;

public record AdminNotificacaoFilaFilter(
        @FilterEquals Long idOrganizacao,
        @FilterLike String destinatario,
        @FilterEquals String canal,
        @FilterLike String status) {
}
