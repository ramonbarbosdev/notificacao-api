package com.notificacao_api.enums;

/**
 * Indica de onde a conversa e montada na listagem.
 * INBOX: historico registrado na plataforma (banco).
 * SESSAO: contato visivel apenas na sessao Baileys/gateway (ex.: tctoken sem inbound na API).
 * SINCRONIZADA: existe nos dois lados.
 */
public enum WhatsappConversaOrigem {
    INBOX,
    SESSAO,
    SINCRONIZADA
}
