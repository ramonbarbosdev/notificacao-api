package com.notificacao_api.model;

import java.time.LocalDateTime;

import com.notificacao_api.common.TipoGlobal;

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
@Table(name = "usuario")
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_usuario")
    @SequenceGenerator(name = "seq_usuario", sequenceName = "seq_usuario", allocationSize = 1)
    @Column(name = "id_usuario")
    private Long idUsuario;

    @Column(name = "nu_cpf", nullable = false, unique = true, length = 11)
    private String nuCpf;

    @Column(name = "nm_usuario")
    private String nmUsuario;

    @Column(name = "nm_email", unique = true)
    private String nmEmail;

    @Column(name = "ds_senha", nullable = false)
    private String dsSenha;

    @Enumerated(EnumType.STRING)
    @Column(name = "tp_global", nullable = false, length = 30)
    private TipoGlobal tpGlobal = TipoGlobal.DEFAULT;

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
