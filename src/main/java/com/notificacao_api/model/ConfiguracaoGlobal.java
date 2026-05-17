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
@Table(name = "configuracao_global")
public class ConfiguracaoGlobal {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_configuracao_global")
    @SequenceGenerator(name = "seq_configuracao_global", sequenceName = "seq_configuracao_global", allocationSize = 1)
    @Column(name = "id_configuracao_global")
    private Long idConfiguracaoGlobal;

    @Column(name = "nm_plataforma", nullable = false)
    private String nmPlataforma = "Notificacao API";

    @Column(name = "nm_dominio_principal")
    private String nmDominioPrincipal;

    @Column(name = "nm_email_suporte")
    private String nmEmailSuporte;

    @Column(name = "ds_smtp_host")
    private String dsSmtpHost;

    @Column(name = "nu_smtp_porta")
    private Integer nuSmtpPorta;

    @Column(name = "nm_smtp_usuario")
    private String nmSmtpUsuario;

    @Column(name = "ds_smtp_senha_criptografada")
    private String dsSmtpSenhaCriptografada;

    @Column(name = "nu_timezone_padrao")
    private Integer nuTimezonePadrao = -3;

    @Column(name = "fl_whatsapp_provider_padrao", nullable = false)
    private Boolean flWhatsappProviderPadrao = true;

    @Column(name = "fl_api_publica_habilitada", nullable = false)
    private Boolean flApiPublicaHabilitada = false;

    @Column(name = "fl_templates_habilitado", nullable = false)
    private Boolean flTemplatesHabilitado = true;

    @Column(name = "fl_webhooks_habilitado", nullable = false)
    private Boolean flWebhooksHabilitado = true;

    @Column(name = "fl_telegram_habilitado", nullable = false)
    private Boolean flTelegramHabilitado = false;

    @Column(name = "fl_email_habilitado", nullable = false)
    private Boolean flEmailHabilitado = false;

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
