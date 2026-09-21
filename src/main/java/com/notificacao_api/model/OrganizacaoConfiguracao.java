package com.notificacao_api.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "organizacao_configuracao")
public class OrganizacaoConfiguracao {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_organizacao_configuracao")
    @SequenceGenerator(name = "seq_organizacao_configuracao", sequenceName = "seq_organizacao_configuracao", allocationSize = 1)
    @Column(name = "id_organizacao_configuracao")
    private Long idOrganizacaoConfiguracao;

    @Column(name = "id_organizacao", nullable = false, unique = true)
    private Long idOrganizacao;

    @Column(name = "nm_exibicao")
    private String nmExibicao;

    @Column(name = "ds_logo_url")
    private String dsLogoUrl;

    @Column(name = "ds_idioma")
    private String dsIdioma = "pt-BR";

    @Column(name = "ds_timezone")
    private String timezone = "America/Bahia";

    @Column(name = "nu_telefone_operacional")
    private String nuTelefoneOperacional;

    @Column(name = "ds_email_operacional")
    private String dsEmailOperacional;

    @Column(name = "ds_email_alertas")
    private String dsEmailAlertas;

    @Column(name = "fl_whatsapp_reconexao_automatica", nullable = false)
    private Boolean whatsappReconexaoAutomatica = true;

    @Column(name = "nu_whatsapp_delay_min_segundos")
    private Integer whatsappDelayMinSegundos = 2;

    @Column(name = "nu_whatsapp_delay_max_segundos")
    private Integer whatsappDelayMaxSegundos = 8;

    @Column(name = "fl_whatsapp_simular_digitando", nullable = false)
    private Boolean whatsappSimularDigitando = true;

    @Column(name = "nu_whatsapp_limite_por_minuto")
    private Integer whatsappLimitePorMinuto = 20;

    @Column(name = "nu_whatsapp_limite_por_dia")
    private Integer whatsappLimitePorDia = 1000;

    @Column(name = "ds_whatsapp_modo_envio")
    private String whatsappModoEnvio = "FILA";

    @Column(name = "fl_templates_versionamento", nullable = false)
    private Boolean templatesVersionamento = true;

    @Column(name = "fl_templates_exigir_aprovacao", nullable = false)
    private Boolean templatesExigirAprovacao = false;

    @Column(name = "fl_templates_validar_variaveis", nullable = false)
    private Boolean templatesValidarVariaveis = true;

    @Column(name = "fl_retry_automatico", nullable = false)
    private Boolean retryAutomatico = true;

    @Column(name = "nu_retry_tentativas")
    private Integer retryTentativas = 3;

    @Column(name = "nu_retry_intervalo_segundos")
    private Integer retryIntervaloSegundos = 60;

    @Column(name = "ds_prioridade_padrao")
    private String prioridadePadrao = "NORMAL";

    @Column(name = "nu_expiracao_fila_horas")
    private Integer expiracaoFilaHoras = 24;

    @Column(name = "fl_auditoria_habilitada", nullable = false)
    private Boolean auditoriaHabilitada = true;

    @Column(name = "ds_webhook_inbound_url")
    private String webhookInboundUrl;

    @Column(name = "ds_webhook_inbound_secret_enc")
    private String webhookInboundSecretEnc;

    @Column(name = "fl_webhook_inbound_habilitado", nullable = false)
    private Boolean webhookInboundHabilitado = false;

    @Column(name = "ds_github_status_disparo", length = 500)
    private String dsGithubStatusDisparo;

    @Column(name = "ds_github_frase_ativacao_whatsapp", length = 500)
    private String dsGithubFraseAtivacaoWhatsapp;

    @Column(name = "fl_webhook_registrar_fila_sem_destinatario", nullable = false)
    private Boolean webhookRegistrarFilaSemDestinatario = true;

    @Column(name = "ds_github_template_assunto_whatsapp", length = 500)
    private String dsGithubTemplateAssuntoWhatsapp;

    @Column(name = "ds_github_template_mensagem_whatsapp", columnDefinition = "text")
    private String dsGithubTemplateMensagemWhatsapp;

    @Column(name = "ds_github_templates_por_cenario", columnDefinition = "text")
    private String dsGithubTemplatesPorCenario;

    @Column(name = "fl_github_nao_notificar_movimentador", nullable = false)
    private Boolean githubNaoNotificarMovimentador = true;

    @Column(name = "fl_github_notificar_status_alterado", nullable = false)
    private Boolean githubNotificarStatusAlterado = true;

    @Column(name = "fl_github_notificar_tarefa_criada", nullable = false)
    private Boolean githubNotificarTarefaCriada = false;

    @Column(name = "fl_github_notificar_responsavel_alterado", nullable = false)
    private Boolean githubNotificarResponsavelAlterado = false;

    @Column(name = "fl_github_notificar_tarefa_atribuida", nullable = false)
    private Boolean githubNotificarTarefaAtribuida = false;

    @Column(name = "fl_github_ignorar_sem_responsavel", nullable = false)
    private Boolean githubIgnorarSemResponsavel = true;

    @Column(name = "ds_github_destinatarios_modo", nullable = false, length = 40)
    private String dsGithubDestinatariosModo = "RESPONSAVEIS";

    @Column(name = "ds_github_destinatarios_extras", length = 500)
    private String dsGithubDestinatariosExtras;

    @Column(name = "fl_github_notificar_issue_fechada_reaberta", nullable = false)
    private Boolean githubNotificarIssueFechadaReaberta = false;

    @Column(name = "fl_github_notificar_issue_label", nullable = false)
    private Boolean githubNotificarIssueLabel = false;

    @Column(name = "fl_github_notificar_somente_campo_status", nullable = false)
    private Boolean githubNotificarSomenteCampoStatus = false;

    @Column(name = "fl_github_notificar_reordenacao", nullable = false)
    private Boolean githubNotificarReordenacao = false;

    @Column(name = "fl_github_pr_avisar_avaliadores", nullable = false)
    private Boolean githubPrAvisarAvaliadores = false;

    @Column(name = "ds_github_pr_status_disparo", length = 500)
    private String dsGithubPrStatusDisparo;

    @Column(name = "ds_github_pr_logins_avaliadores", length = 500)
    private String dsGithubPrLoginsAvaliadores;

    @Column(name = "fl_github_issue_avisar_avaliadores", nullable = false)
    private Boolean githubIssueAvisarAvaliadores = false;

    @Column(name = "ds_github_issue_status_disparo", length = 500)
    private String dsGithubIssueStatusDisparo;

    @Column(name = "ds_github_graphql_token_enc", columnDefinition = "text")
    private String dsGithubGraphqlTokenEnc;

    @Column(name = "nu_github_app_id")
    private Long nuGithubAppId;

    @Column(name = "ds_github_app_private_key_enc", columnDefinition = "text")
    private String dsGithubAppPrivateKeyEnc;

    @Column(name = "nu_github_installation_id")
    private Long nuGithubInstallationId;

    @Column(name = "ds_github_graphql_url", length = 500)
    private String dsGithubGraphqlUrl;

    @Column(name = "ds_github_api_base_url", length = 500)
    private String dsGithubApiBaseUrl;

    @Column(name = "nu_github_http_connect_timeout_ms")
    private Integer nuGithubHttpConnectTimeoutMs;

    @Column(name = "nu_github_http_read_timeout_ms")
    private Integer nuGithubHttpReadTimeoutMs;

    @Column(name = "nu_github_installation_token_skew_segundos")
    private Integer nuGithubInstallationTokenSkewSegundos;

    @Column(name = "dt_criacao", nullable = false, updatable = false)
    private LocalDateTime dtCriacao;

    @Column(name = "dt_atualizacao", nullable = false)
    private LocalDateTime dtAtualizacao;

    @PrePersist
    void prePersist() {
        LocalDateTime agora = LocalDateTime.now();
        dtCriacao = agora;
        dtAtualizacao = agora;
    }

    @PreUpdate
    void preUpdate() {
        dtAtualizacao = LocalDateTime.now();
    }
}
