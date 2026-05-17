package com.notificacao_api.dto.configuracao;

import java.time.LocalDateTime;

public record OrganizacaoConfiguracaoResponse(
        Long idOrganizacaoConfiguracao,
        Long idOrganizacao,
        String nmExibicao,
        String dsLogoUrl,
        String dsIdioma,
        String timezone,
        String nuTelefoneOperacional,
        String dsEmailOperacional,
        Boolean whatsappReconexaoAutomatica,
        Integer whatsappDelayMinSegundos,
        Integer whatsappDelayMaxSegundos,
        Boolean whatsappSimularDigitando,
        Integer whatsappLimitePorMinuto,
        Integer whatsappLimitePorDia,
        String whatsappModoEnvio,
        Boolean exigirConsentimento,
        Boolean consentimentoExpira,
        Integer diasExpiracaoConsentimento,
        Boolean bloqueioAutomatico,
        Integer limiteFalhasParaBloqueio,
        Boolean templatesVersionamento,
        Boolean templatesExigirAprovacao,
        Boolean templatesValidarVariaveis,
        Boolean retryAutomatico,
        Integer retryTentativas,
        Integer retryIntervaloSegundos,
        String prioridadePadrao,
        Integer expiracaoFilaHoras,
        Boolean auditoriaHabilitada,
        LocalDateTime dtCriacao,
        LocalDateTime dtAtualizacao) {
}
