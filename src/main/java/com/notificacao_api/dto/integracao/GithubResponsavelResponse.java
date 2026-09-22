package com.notificacao_api.dto.integracao;

import java.time.LocalDateTime;

public record GithubResponsavelResponse(
        Long idGithubResponsavel,
        String githubLogin,
        String whatsappMascarado,
        boolean habilitado,
        boolean ativo,
        LocalDateTime dtAtualizacao,
        String whatsappAnteriorMascarado,
        LocalDateTime dtMudancaWhatsapp,
        String alertaAdministrador) {
}
