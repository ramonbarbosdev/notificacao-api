package com.notificacao_api.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "usuario_organizacao")
public class UsuarioOrganizacao {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_usuario_organizacao")
    @SequenceGenerator(name = "seq_usuario_organizacao", sequenceName = "seq_usuario_organizacao", allocationSize = 1)
    @Column(name = "id_usuario_organizacao")
    private Long idUsuarioOrganizacao;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario", nullable = false)
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_organizacao", nullable = false)
    private Organizacao organizacao;

    @Column(name = "ds_role", nullable = false, length = 30)
    private String dsRole;

    @Column(name = "fl_ativo", nullable = false)
    private Boolean flAtivo = true;

    @Column(name = "dt_criacao", nullable = false)
    private LocalDateTime dtCriacao;

    @PrePersist
    void prePersist() {
        dtCriacao = LocalDateTime.now();
    }
}
