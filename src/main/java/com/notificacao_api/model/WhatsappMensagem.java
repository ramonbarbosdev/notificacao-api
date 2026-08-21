package com.notificacao_api.model;

import java.time.LocalDateTime;

import com.notificacao_api.enums.WhatsappMensagemDirecao;
import com.notificacao_api.enums.WhatsappMensagemStatus;
import com.notificacao_api.enums.WhatsappMensagemTipo;
import com.notificacao_api.enums.WhatsappProvedorEnvio;

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
@Table(name = "whatsapp_mensagem")
public class WhatsappMensagem {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_whatsapp_mensagem")
    @SequenceGenerator(
            name = "seq_whatsapp_mensagem",
            sequenceName = "seq_whatsapp_mensagem",
            allocationSize = 1)
    @Column(name = "id_mensagem")
    private Long idMensagem;

    @Column(name = "id_organizacao", nullable = false)
    private Long idOrganizacao;

    @Column(name = "id_notificacao")
    private Long idNotificacao;

    @Enumerated(EnumType.STRING)
    @Column(name = "tp_provider", nullable = false, length = 30)
    private WhatsappProvedorEnvio provider;

    @Column(name = "ds_telefone", nullable = false, length = 32)
    private String telefone;

    @Enumerated(EnumType.STRING)
    @Column(name = "tp_direcao", nullable = false, length = 16)
    private WhatsappMensagemDirecao direcao;

    @Enumerated(EnumType.STRING)
    @Column(name = "tp_mensagem", nullable = false, length = 16)
    private WhatsappMensagemTipo tipo;

    @Column(name = "ds_categoria", length = 64)
    private String categoria;

    @Column(name = "nm_template")
    private String templateName;

    @Column(name = "ds_id_externo", length = 128)
    private String idExterno;

    @Enumerated(EnumType.STRING)
    @Column(name = "tp_status", nullable = false, length = 16)
    private WhatsappMensagemStatus status = WhatsappMensagemStatus.PENDING;

    @Column(name = "ds_codigo_erro", length = 64)
    private String codigoErro;

    @Column(name = "ds_erro", columnDefinition = "text")
    private String erro;

    @Column(name = "dt_envio")
    private LocalDateTime dtEnvio;

    @Column(name = "dt_entrega")
    private LocalDateTime dtEntrega;

    @Column(name = "dt_leitura")
    private LocalDateTime dtLeitura;

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
