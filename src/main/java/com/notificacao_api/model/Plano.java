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
@Table(name = "plano")
public class Plano {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_plano")
    @SequenceGenerator(name = "seq_plano", sequenceName = "seq_plano", allocationSize = 1)
    @Column(name = "id_plano")
    private Long idPlano;

    @Column(name = "nm_plano", nullable = false)
    private String nmPlano;

    @Column(name = "ds_plano", columnDefinition = "text")
    private String dsPlano;

    @Column(name = "nu_limite_mensagens_mensal")
    private Integer nuLimiteMensagensMensal;

    @Column(name = "nu_limite_usuarios")
    private Integer nuLimiteUsuarios;

    @Column(name = "nu_limite_templates")
    private Integer nuLimiteTemplates;

    @Column(name = "nu_limite_contatos")
    private Integer nuLimiteContatos;

    @Column(name = "fl_whatsapp_habilitado", nullable = false)
    private Boolean flWhatsappHabilitado = true;

    @Column(name = "fl_email_habilitado", nullable = false)
    private Boolean flEmailHabilitado = false;

    @Column(name = "fl_telegram_habilitado", nullable = false)
    private Boolean flTelegramHabilitado = false;

    @Column(name = "fl_webhook_habilitado", nullable = false)
    private Boolean flWebhookHabilitado = true;

    @Column(name = "fl_api_publica_habilitada", nullable = false)
    private Boolean flApiPublicaHabilitada = false;

    @Column(name = "fl_ativo", nullable = false)
    private Boolean flAtivo = true;

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
