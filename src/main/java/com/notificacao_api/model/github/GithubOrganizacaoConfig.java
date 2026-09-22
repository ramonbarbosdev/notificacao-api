package com.notificacao_api.model.github;

import lombok.Getter;
import lombok.Setter;

/**
 * Visão agregada da configuração GitHub da organização (integração + módulo Project v2).
 * Substitui os antigos campos em {@code OrganizacaoConfiguracao}.
 */
@Getter
@Setter
public class GithubOrganizacaoConfig {

    private Long idOrganizacao;

    private String dsGithubFraseAtivacaoWhatsapp;
    private String dsGithubOrganizationLogin;
    private String dsGithubGraphqlTokenEnc;
    private Long nuGithubAppId;
    private String dsGithubAppPrivateKeyEnc;
    private Long nuGithubInstallationId;
    private String dsGithubGraphqlUrl;
    private String dsGithubApiBaseUrl;
    private Integer nuGithubHttpConnectTimeoutMs;
    private Integer nuGithubHttpReadTimeoutMs;
    private Integer nuGithubInstallationTokenSkewSegundos;

    private String dsGithubProjectV2NodeId;
    private Integer nuGithubProjectV2Number;
    private String dsGithubStatusDisparo;
    private String dsGithubStatusDisparoGatilhos;
    private String dsGithubRegrasPorStatus;
    private String dsGithubTemplateAssuntoWhatsapp;
    private String dsGithubTemplateMensagemWhatsapp;
    private String dsGithubTemplatesPorCenario;

    private Boolean githubNaoNotificarMovimentador = true;
    private Boolean githubNotificarStatusAlterado = true;
    private Boolean githubNotificarTarefaCriada = false;
    private Boolean githubNotificarResponsavelAlterado = false;
    private Boolean githubNotificarTarefaAtribuida = false;
    private Boolean githubIgnorarSemResponsavel = true;
    private String dsGithubDestinatariosModo = "RESPONSAVEIS";
    private String dsGithubDestinatariosExtras;
    private Boolean githubNotificarIssueFechadaReaberta = false;
    private Boolean githubNotificarIssueLabel = false;
    private Boolean githubNotificarSomenteCampoStatus = false;
    private Boolean githubNotificarReordenacao = false;
    private Boolean githubPrAvisarAvaliadores = false;
    private String dsGithubPrStatusDisparo;
    private String dsGithubPrLoginsAvaliadores;
    private Boolean githubIssueAvisarAvaliadores = false;
    private String dsGithubIssueStatusDisparo;
}
