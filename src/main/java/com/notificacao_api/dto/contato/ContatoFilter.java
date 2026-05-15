package com.notificacao_api.dto.contato;

import com.notificacao_api.shared.FilterEquals;
import com.notificacao_api.shared.FilterLike;

public record ContatoFilter(

                @FilterLike String destinatario,

                @FilterEquals String canal


) {
}