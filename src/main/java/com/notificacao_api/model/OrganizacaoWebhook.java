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
@Table(name = "organizacao_webhook")
public class OrganizacaoWebhook {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_organizacao_webhook")
    @SequenceGenerator(name = "seq_organizacao_webhook", sequenceName = "seq_organizacao_webhook", allocationSize = 1)
    @Column(name = "id_webhook")
    private Long idWebhook;

    @Column(name = "id_organizacao", nullable = false)
    private Long idOrganizacao;

    @Column(name = "nm_webhook", nullable = false)
    private String nome;

    @Column(name = "ds_url", nullable = false)
    private String url;

    @Column(name = "ds_secret_hash", nullable = false)
    private String secretHash;

    @Column(name = "ds_eventos", nullable = false, columnDefinition = "text")
    private String eventos;

    @Column(name = "fl_ativo", nullable = false)
    private Boolean ativo = true;

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
