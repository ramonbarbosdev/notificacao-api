package com.notificacao_api.dto.pagamento;

import com.notificacao_api.enums.TipoMetodoPagamento;

public record MetodoPagamentoResponse(
        Long idMetodoPagamento,
        TipoMetodoPagamento tipo,
        String ultimos4Digitos,
        String bandeira,
        Boolean padrao) {
}
