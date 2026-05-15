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
@Table(name = "contato_notificacao")
public class Contato {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_contato_notificacao")
    @SequenceGenerator(name = "seq_contato_notificacao", sequenceName = "seq_contato_notificacao", allocationSize = 1)
    @Column(name = "id_contato")
    private Long idContato;

    @Column(name = "id_organizacao", nullable = false)
    private Long idOrganizacao;

    @Enumerated(EnumType.STRING)
    @Column(name = "tp_canal", nullable = false, length = 30)
    private CanalNotificacao canal;

    @Column(name = "ds_destinatario", nullable = false)
    private String destinatario;

    @Column(name = "fl_consentimento", nullable = false)
    private Boolean consentimento = false;

    @Column(name = "fl_bloqueado", nullable = false)
    private Boolean bloqueado = false;

    @Column(name = "ds_motivo_bloqueio")
    private String motivoBloqueio;

    @Column(name = "dt_consentimento")
    private LocalDateTime dtConsentimento;

    @Column(name = "dt_bloqueio")
    private LocalDateTime dtBloqueio;

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
