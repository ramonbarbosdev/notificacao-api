package com.notificacao_api.dto.whatsapp;

import java.util.List;

import com.notificacao_api.enums.TcTokenAudienciaOrigem;
import com.notificacao_api.enums.TcTokenAudienciaSituacao;

public record TcTokenAudienciaLinhaResponse(
        String telefone,
        String telefoneMascarado,
        String nomeExibicao,
        List<TcTokenAudienciaOrigem> origens,
        Double idadeDias,
        Double expiraEmDias,
        TcTokenAudienciaSituacao situacao,
        String situacaoRotulo,
        Boolean prontoParaEnvio,
        Boolean liberadoIndisponivel,
        String jidComToken,
        boolean consultadoNoGateway) {
}
