package com.notificacao_api.model.github;

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
@Table(name = "github_kanban_webhook_evento_pendente")
public class GithubKanbanWebhookEventoPendente {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_github_kanban_webhook_evento_pendente")
    @SequenceGenerator(
            name = "seq_github_kanban_webhook_evento_pendente",
            sequenceName = "seq_github_kanban_webhook_evento_pendente",
            allocationSize = 1)
    @Column(name = "id_github_kanban_webhook_evento")
    private Long id;

    @Column(name = "id_organizacao", nullable = false)
    private Long idOrganizacao;

    @Column(name = "ds_delivery_id", length = 120)
    private String dsDeliveryId;

    @Column(name = "ds_chave_dedup", length = 500)
    private String dsChaveDedup;

    @Column(name = "ds_payload_json", nullable = false, columnDefinition = "text")
    private String dsPayloadJson;

    @Column(name = "dt_ocorrido", nullable = false)
    private LocalDateTime dtOcorrido;

    @Column(name = "dt_criacao", nullable = false, updatable = false)
    private LocalDateTime dtCriacao;

    @PrePersist
    void prePersist() {
        if (dtCriacao == null) {
            dtCriacao = LocalDateTime.now();
        }
    }
}
