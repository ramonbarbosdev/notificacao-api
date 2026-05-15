package com.notificacao_api.model;

import java.time.LocalDateTime;

import com.notificacao_api.enums.CanalNotificacao;
import com.notificacao_api.enums.EventoAuditoriaNotificacao;
import com.notificacao_api.enums.StatusNotificacao;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "notificacao_auditoria")
public class AuditoriaNotificacao {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_notificacao_auditoria")
    @SequenceGenerator(name = "seq_notificacao_auditoria", sequenceName = "seq_notificacao_auditoria", allocationSize = 1)
    @Column(name = "id_auditoria")
    private Long idAuditoria;

    @Column(name = "id_notificacao", nullable = false)
    private Long idNotificacao;

    @Column(name = "id_organizacao", nullable = false)
    private Long idOrganizacao;

    @Enumerated(EnumType.STRING)
    @Column(name = "tp_canal", nullable = false, length = 30)
    private CanalNotificacao canal;

    @Column(name = "ds_destinatario", nullable = false)
    private String destinatario;

    @Enumerated(EnumType.STRING)
    @Column(name = "tp_status", nullable = false, length = 30)
    private StatusNotificacao status;

    @Enumerated(EnumType.STRING)
    @Column(name = "tp_evento", nullable = false, length = 30)
    private EventoAuditoriaNotificacao evento;

    @Column(name = "nm_provedor")
    private String provedor;

    @Column(name = "nu_tentativa", nullable = false)
    private Integer tentativa;

    @Column(name = "ds_erro", columnDefinition = "text")
    private String erro;

    @Column(name = "dt_criacao", nullable = false, updatable = false)
    private LocalDateTime dtCriacao;

    @PrePersist
    void prePersist() {
        dtCriacao = LocalDateTime.now();
    }
}
