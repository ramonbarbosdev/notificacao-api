package com.notificacao_api.service.github;

/**
 * Destinatário WhatsApp resolvido a partir de login GitHub com opt-in.
 */
public record GithubWhatsappDestinatario(String telefone, String githubLogin, String nomeExibicao) {}
