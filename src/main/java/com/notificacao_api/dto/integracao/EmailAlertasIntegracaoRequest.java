package com.notificacao_api.dto.integracao;

import jakarta.validation.constraints.Email;

public record EmailAlertasIntegracaoRequest(
        @Email String dsEmailAlertas) {
}
