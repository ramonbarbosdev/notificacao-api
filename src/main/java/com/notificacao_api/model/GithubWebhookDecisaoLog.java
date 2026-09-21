package com.notificacao_api.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "github_webhook_decisao_log")
public class GithubWebhookDecisaoLog {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_github_webhook_decisao_log")
    @SequenceGenerator(
            name = "seq_github_webhook_decisao_log",
            sequenceName = "seq_github_webhook_decisao_log",
            allocationSize = 1)
    @Column(name = "id_github_webhook_decisao_log")
    private Long idGithubWebhookDecisaoLog;

    @Column(name = "id_organizacao", nullable = false)
    private Long idOrganizacao;

    @Column(name = "ds_delivery_id", length = 120)
    private String dsDeliveryId;

    @Column(name = "ds_github_event", length = 80)
    private String dsGithubEvent;

    @Column(name = "ds_action", length = 80)
    private String dsAction;

    @Column(name = "ds_resultado", nullable = false, length = 60)
    private String dsResultado;

    @Column(name = "ds_descricao", nullable = false, columnDefinition = "text")
    private String dsDescricao;

    @Column(name = "ds_titulo_card", length = 500)
    private String dsTituloCard;

    @Column(name = "ds_status_destino", length = 200)
    private String dsStatusDestino;

    @Column(name = "ds_status_anterior", length = 200)
    private String dsStatusAnterior;

    @Column(name = "fl_pull_request", nullable = false)
    private boolean flPullRequest;

    @Column(name = "fl_issue_project_v2", nullable = false)
    private boolean flIssueProjectV2;

    @Column(name = "ds_fluxo_destinatarios", length = 40)
    private String dsFluxoDestinatarios;

    @Column(name = "ds_logins_destino", columnDefinition = "text")
    private String dsLoginsDestino;

    @Column(name = "nu_whatsapp_enfileirados", nullable = false)
    private int nuWhatsappEnfileirados;

    @Column(name = "ds_detalhe_json", columnDefinition = "text")
    private String dsDetalheJson;

    @Column(name = "dt_criacao", nullable = false)
    private LocalDateTime dtCriacao;

    @PrePersist
    void prePersist() {
        if (dtCriacao == null) {
            dtCriacao = LocalDateTime.now();
        }
    }
}
