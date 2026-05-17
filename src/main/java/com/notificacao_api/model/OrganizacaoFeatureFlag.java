package com.notificacao_api.model;

import java.time.LocalDateTime;

import com.notificacao_api.enums.RecursoFeature;

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
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(
        name = "organizacao_feature_flag",
        uniqueConstraints = @UniqueConstraint(
                name = "ux_organizacao_feature_flag_org_recurso",
                columnNames = { "id_organizacao", "ds_recurso" }))
public class OrganizacaoFeatureFlag {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_organizacao_feature_flag")
    @SequenceGenerator(name = "seq_organizacao_feature_flag", sequenceName = "seq_organizacao_feature_flag", allocationSize = 1)
    @Column(name = "id_feature_flag")
    private Long idFeatureFlag;

    @Column(name = "id_organizacao", nullable = false)
    private Long idOrganizacao;

    @Enumerated(EnumType.STRING)
    @Column(name = "ds_recurso", nullable = false, length = 50)
    private RecursoFeature recurso;

    @Column(name = "fl_habilitado", nullable = false)
    private Boolean habilitado = true;

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
