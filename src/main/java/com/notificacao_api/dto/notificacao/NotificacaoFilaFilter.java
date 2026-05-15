package com.notificacao_api.dto.notificacao;

import com.notificacao_api.shared.FilterEquals;
import com.notificacao_api.shared.FilterLike;

public record NotificacaoFilaFilter(

                @FilterLike String destinatario,
                
                @FilterEquals String canal,
                @FilterLike String status


) {
}