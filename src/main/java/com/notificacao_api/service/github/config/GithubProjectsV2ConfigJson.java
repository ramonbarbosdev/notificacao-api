package com.notificacao_api.service.github.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

@JsonIgnoreProperties(ignoreUnknown = true)
public class GithubProjectsV2ConfigJson {

    public int versao = 1;
    public String projectV2NodeId;
    public Integer projectV2Number;
    public String statusDisparo;
    public String statusDisparoGatilhos;
    public String regrasPorStatus;
    public String templateAssuntoWhatsapp;
    public String templateMensagemWhatsapp;
    public JsonNode templatesPorCenario;
    public Boolean naoNotificarMovimentador;
    public Boolean notificarStatusAlterado;
    public Boolean notificarTarefaCriada;
    public Boolean notificarResponsavelAlterado;
    public Boolean notificarTarefaAtribuida;
    public Boolean ignorarSemResponsavel;
    public String destinatariosModo;
    public String destinatariosExtras;
    public Boolean notificarIssueFechadaReaberta;
    public Boolean notificarIssueLabel;
    public Boolean notificarSomenteCampoStatus;
    public Boolean notificarReordenacao;
    public Boolean prAvisarAvaliadores;
    public String prStatusDisparo;
    public String prLoginsAvaliadores;
    public Boolean issueAvisarAvaliadores;
    public String issueStatusDisparo;
}
