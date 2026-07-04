package com.notificacao_api.dto.whatsapp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record StatusWhatsappResposta(
        Boolean sucesso,
        Long idOrganizacao,
        String status,
        Boolean conectado,
        String qr,
        String qrImagem,
        String telefone,
        String erro,
        SessaoOperacionalContextoDTO operacional) {

    public static StatusWhatsappResposta respostaGateway(
            Boolean sucesso,
            Long idOrganizacao,
            String status,
            Boolean conectado,
            String qr,
            String qrImagem,
            String telefone,
            String erro) {
        return new StatusWhatsappResposta(
                sucesso, idOrganizacao, status, conectado, qr, qrImagem, telefone, erro, null);
    }

    public StatusWhatsappResposta comOperacional(SessaoOperacionalContextoDTO operacional) {
        return new StatusWhatsappResposta(
                sucesso, idOrganizacao, status, conectado, qr, qrImagem, telefone, erro, operacional);
    }
}
