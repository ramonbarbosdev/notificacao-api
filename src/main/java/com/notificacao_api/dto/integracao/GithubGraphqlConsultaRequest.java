package com.notificacao_api.dto.integracao;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GithubGraphqlConsultaRequest(
        @NotBlank @Size(max = 200) String nodeId,
        @Size(max = 80) String contentType) {
}
