package com.notificacao_api.model;

import java.time.LocalDateTime;

import com.notificacao_api.enums.WhatsappSessionStatus;

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
@Table(name = "whatsapp_sessao")
public class WhatsappSession {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_whatsapp_sessao")
    @SequenceGenerator(name = "seq_whatsapp_sessao", sequenceName = "seq_whatsapp_sessao", allocationSize = 1)
    @Column(name = "id_whatsappsession")
    private Long idWhatsappSession;

    @Column(name = "id_organizacao", nullable = false)
    private Long idOrganizacao;

    @Enumerated(EnumType.STRING)
    @Column(name = "tp_status", nullable = false, length = 30)
    private WhatsappSessionStatus tpStatus;

    @Column(name = "nu_telefone")
    private String nuTelefone;

    @Column(name = "ds_sessionpath", nullable = false)
    private String dsSessionPath;

    @Column(name = "dt_ultimaconexao")
    private LocalDateTime dtUltimaConexao;

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
