package com.notificacao_api.model.github;

import java.time.LocalDateTime;

import com.notificacao_api.enums.GithubKanbanWebhookModoEnvio;

import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "organizacao_github_integracao")
public class OrganizacaoGithubIntegracao {

    @Id
    @Column(name = "id_organizacao")
    private Long idOrganizacao;

    @Column(name = "ds_frase_ativacao_whatsapp", length = 500)
    private String dsFraseAtivacaoWhatsapp;

    @Column(name = "ds_organization_login", length = 120)
    private String dsOrganizationLogin;

    @Column(name = "ds_graphql_token_enc", columnDefinition = "text")
    private String dsGraphqlTokenEnc;

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

    @Column(name = "fl_github_kanban_movimentacao_webhook_habilitado", nullable = false)
    private Boolean flGithubKanbanMovimentacaoWebhookHabilitado = false;

    @Column(name = "ds_github_kanban_movimentacao_webhook_url", length = 500)
    private String dsGithubKanbanMovimentacaoWebhookUrl;

    @Column(name = "ds_github_kanban_movimentacao_webhook_auth_enc", length = 2000)
    private String dsGithubKanbanMovimentacaoWebhookAuthEnc;

    @Column(name = "fl_github_whatsapp_direto_habilitado", nullable = false)
    private Boolean flGithubWhatsappDiretoHabilitado = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "ds_github_kanban_webhook_modo_envio", nullable = false, length = 20)
    private GithubKanbanWebhookModoEnvio dsGithubKanbanWebhookModoEnvio = GithubKanbanWebhookModoEnvio.LOTE;

    @Column(name = "nu_github_kanban_webhook_intervalo_minutos", nullable = false)
    private Integer nuGithubKanbanWebhookIntervaloMinutos = 30;

    @Column(name = "dt_github_kanban_webhook_ultimo_flush")
    private LocalDateTime dtGithubKanbanWebhookUltimoFlush;

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
