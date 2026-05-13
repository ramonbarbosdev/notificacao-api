package com.notificacao_api.dto.whatsapp;

public record EnviarMensagemWhatsappResposta(
        Boolean sucesso,
        Long idOrganizacao,
        String identificadorContato,
        String telefone,
        String estrategia,
        String erro
) {}
