package com.notificacao_api.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "auditoria_evento")
public class AuditoriaEvento {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_auditoria_evento")
    @SequenceGenerator(name = "seq_auditoria_evento", sequenceName = "seq_auditoria_evento", allocationSize = 1)
    @Column(name = "id_auditoria")
    private Long idAuditoria;

    @Column(name = "id_organizacao")
    private Long idOrganizacao;

    @Column(name = "id_usuario")
    private Long idUsuario;

    @Column(name = "ds_role")
    private String role;

    @Column(name = "nm_modulo", nullable = false)
    private String modulo;

    @Column(name = "nm_acao", nullable = false)
    private String acao;

    @Column(name = "ds_descricao", columnDefinition = "text")
    private String descricao;

    @Column(name = "ds_ip")
    private String ip;

    @Column(name = "ds_user_agent")
    private String userAgent;

    @Column(name = "ds_dados_antes", columnDefinition = "text")
    private String dadosAntes;

    @Column(name = "ds_dados_depois", columnDefinition = "text")
    private String dadosDepois;

    @Column(name = "dt_criacao", nullable = false, updatable = false)
    private LocalDateTime dtCriacao;

    @PrePersist
    void prePersist() {
        dtCriacao = LocalDateTime.now();
    }
}
