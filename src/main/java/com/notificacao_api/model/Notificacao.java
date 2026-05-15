package com.notificacao_api.model;

import java.time.LocalDateTime;

import com.notificacao_api.enums.CanalNotificacao;
import com.notificacao_api.enums.StatusNotificacao;

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
@Table(name = "notificacao")
public class Notificacao {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_notificacao")
    @SequenceGenerator(name = "seq_notificacao", sequenceName = "seq_notificacao", allocationSize = 1)
    @Column(name = "id_notificacao")
    private Long idNotificacao;

    @Column(name = "id_organizacao", nullable = false)
    private Long idOrganizacao;

    @Enumerated(EnumType.STRING)
    @Column(name = "tp_canal", nullable = false, length = 30)
    private CanalNotificacao canal;

    @Column(name = "ds_destinatario", nullable = false)
    private String destinatario;

    @Column(name = "ds_assunto")
    private String assunto;

    @Column(name = "ds_mensagem", nullable = false, columnDefinition = "text")
    private String mensagem;

    @Enumerated(EnumType.STRING)
    @Column(name = "tp_status", nullable = false, length = 30)
    private StatusNotificacao status = StatusNotificacao.PENDENTE;

    @Column(name = "ds_erro", columnDefinition = "text")
    private String erro;

    @Column(name = "nm_provedor")
    private String provedor;

    @Column(name = "nu_tentativas", nullable = false)
    private Integer tentativas = 0;

    @Column(name = "nu_tentativas_maximas", nullable = false)
    private Integer tentativasMaximas = 3;

    @Column(name = "ds_hash_deduplicacao", length = 128)
    private String hashDeduplicacao;

    @Column(name = "dt_proxima_tentativa", nullable = false)
    private LocalDateTime dtProximaTentativa;

    @Column(name = "dt_ultimo_processamento")
    private LocalDateTime dtUltimoProcessamento;

    @Column(name = "dt_envio")
    private LocalDateTime dtEnvio;

    @Column(name = "dt_criacao", nullable = false, updatable = false)
    private LocalDateTime dtCriacao;

    @Column(name = "dt_atualizacao", nullable = false)
    private LocalDateTime dtAtualizacao;

    @PrePersist
    void prePersist() {
        LocalDateTime agora = LocalDateTime.now();
        dtCriacao = agora;
        dtAtualizacao = agora;
        if (dtProximaTentativa == null) {
            dtProximaTentativa = agora;
        }
    }

    @PreUpdate
    void preUpdate() {
        dtAtualizacao = LocalDateTime.now();
    }
}
