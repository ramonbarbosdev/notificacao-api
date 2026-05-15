package com.notificacao_api.model;

import java.time.LocalDateTime;

import com.notificacao_api.enums.CanalNotificacao;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "notificacao_modelo")
public class TemplateNotificacao {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_notificacao_modelo")
    @SequenceGenerator(name = "seq_notificacao_modelo", sequenceName = "seq_notificacao_modelo", allocationSize = 1)
    @Column(name = "id_modelo")
    private Long idModelo;

    @Column(name = "id_organizacao", nullable = false)
    private Long idOrganizacao;

    @Enumerated(EnumType.STRING)
    @Column(name = "tp_canal", nullable = false, length = 30)
    private CanalNotificacao canal;

    @Column(name = "nm_modelo", nullable = false)
    private String nome;

    @Column(name = "ds_assunto")
    private String assunto;

    @Column(name = "ds_corpo", nullable = false, columnDefinition = "text")
    private String corpo;

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
