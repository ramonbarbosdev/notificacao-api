package com.notificacao_api.dto.whatsapp;

public record StatusWhatsappResposta(
        Boolean sucesso,
        Long idOrganizacao,
        String status,
        Boolean conectado,
        String qr,
        String qrImagem,
        String telefone,
        String erro
) {}
