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
@Table(name = "alerta_operacional")
public class AlertaOperacional {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_alerta_operacional")
    @SequenceGenerator(name = "seq_alerta_operacional", sequenceName = "seq_alerta_operacional", allocationSize = 1)
    @Column(name = "id_alerta")
    private Long idAlerta;

    @Column(name = "id_organizacao")
    private Long idOrganizacao;

    @Column(name = "id_notificacao")
    private Long idNotificacao;

    @Column(name = "tp_origem", nullable = false, length = 50)
    private String tpOrigem;

    @Column(name = "ds_titulo", nullable = false)
    private String dsTitulo;

    @Column(name = "ds_mensagem", nullable = false, columnDefinition = "TEXT")
    private String dsMensagem;

    @Column(name = "ds_destinatario")
    private String dsDestinatario;

    @Column(name = "ds_canal", length = 30)
    private String dsCanal;

    @Column(name = "ds_codigo_erro", length = 80)
    private String dsCodigoErro;

    @Column(name = "fl_email_enviado", nullable = false)
    private boolean flEmailEnviado = false;

    @Column(name = "dt_criacao", nullable = false, updatable = false)
    private LocalDateTime dtCriacao;

    @PrePersist
    void prePersist() {
        if (dtCriacao == null) {
            dtCriacao = LocalDateTime.now();
        }
    }
}
