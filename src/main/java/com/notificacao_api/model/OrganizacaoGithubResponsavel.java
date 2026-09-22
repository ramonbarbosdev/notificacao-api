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
@Table(name = "organizacao_github_responsavel")
public class OrganizacaoGithubResponsavel {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_organizacao_github_responsavel")
    @SequenceGenerator(
            name = "seq_organizacao_github_responsavel",
            sequenceName = "seq_organizacao_github_responsavel",
            allocationSize = 1)
    @Column(name = "id_github_responsavel")
    private Long idGithubResponsavel;

    @Column(name = "id_organizacao", nullable = false)
    private Long idOrganizacao;

    /** Nulo enquanto o opt-in via WhatsApp aguarda o login do GitHub. */
    @Column(name = "ds_github_login", length = 100)
    private String dsGithubLogin;

    @Column(name = "nu_whatsapp", nullable = false, length = 20)
    private String nuWhatsapp;

    @Column(name = "fl_ativo", nullable = false)
    private Boolean ativo = true;

    /** Ultimo numero antes de opt-in em outro WhatsApp (mesmo login). */
    @Column(name = "nu_whatsapp_anterior", length = 20)
    private String nuWhatsappAnterior;

    @Column(name = "dt_mudanca_whatsapp")
    private LocalDateTime dtMudancaWhatsapp;

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
