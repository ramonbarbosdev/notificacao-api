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
@Table(name = "organizacao")
public class Organizacao {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_organizacao")
    @SequenceGenerator(name = "seq_organizacao", sequenceName = "seq_organizacao", allocationSize = 1)
    @Column(name = "id_organizacao")
    private Long idOrganizacao;

    @Column(name = "nm_organizacao", nullable = false)
    private String nmOrganizacao;

    @Column(name = "ds_documento")
    private String dsDocumento;

    @Column(name = "fl_ativo", nullable = false)
    private Boolean flAtivo = true;

    @Column(name = "dt_criacao", nullable = false)
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
