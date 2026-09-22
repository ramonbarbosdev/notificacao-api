package com.notificacao_api.dto.integracao.github;

import java.util.Map;

import com.notificacao_api.dto.configuracao.GithubTemplatePorCenarioDto;

public record GithubIntegracaoProjectsV2PatchRequest(
        String dsGithubProjectV2NodeId,
        Integer nuGithubProjectV2Number,
        String dsGithubStatusDisparo,
        String dsGithubStatusDisparoGatilhos,
        String dsGithubRegrasPorStatus,
        String dsGithubTemplateAssuntoWhatsapp,
        String dsGithubTemplateMensagemWhatsapp,
        Map<String, GithubTemplatePorCenarioDto> githubTemplatesPorCenario,
        Boolean githubNaoNotificarMovimentador,
        Boolean githubNotificarStatusAlterado,
        Boolean githubNotificarTarefaCriada,
        Boolean githubNotificarResponsavelAlterado,
        Boolean githubNotificarTarefaAtribuida,
        Boolean githubIgnorarSemResponsavel,
        String dsGithubDestinatariosModo,
        String dsGithubDestinatariosExtras,
        Boolean githubNotificarIssueFechadaReaberta,
        Boolean githubNotificarIssueLabel,
        Boolean githubNotificarSomenteCampoStatus,
        Boolean githubNotificarReordenacao,
        Boolean githubPrAvisarAvaliadores,
        String dsGithubPrStatusDisparo,
        String dsGithubPrLoginsAvaliadores,
        Boolean githubIssueAvisarAvaliadores,
        String dsGithubIssueStatusDisparo) {
}
