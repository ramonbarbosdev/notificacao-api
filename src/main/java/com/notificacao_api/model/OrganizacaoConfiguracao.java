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
@Table(name = "organizacao_configuracao")
public class OrganizacaoConfiguracao {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_organizacao_configuracao")
    @SequenceGenerator(name = "seq_organizacao_configuracao", sequenceName = "seq_organizacao_configuracao", allocationSize = 1)
    @Column(name = "id_organizacao_configuracao")
    private Long idOrganizacaoConfiguracao;

    @Column(name = "id_organizacao", nullable = false, unique = true)
    private Long idOrganizacao;

    @Column(name = "nm_exibicao")
    private String nmExibicao;

    @Column(name = "ds_logo_url")
    private String dsLogoUrl;

    @Column(name = "ds_idioma")
    private String dsIdioma = "pt-BR";

    @Column(name = "ds_timezone")
    private String timezone = "America/Bahia";

    @Column(name = "nu_telefone_operacional")
    private String nuTelefoneOperacional;

    @Column(name = "ds_email_operacional")
    private String dsEmailOperacional;

    @Column(name = "ds_email_alertas")
    private String dsEmailAlertas;

    @Column(name = "fl_whatsapp_reconexao_automatica", nullable = false)
    private Boolean whatsappReconexaoAutomatica = true;

    @Column(name = "nu_whatsapp_delay_min_segundos")
    private Integer whatsappDelayMinSegundos = 2;

    @Column(name = "nu_whatsapp_delay_max_segundos")
    private Integer whatsappDelayMaxSegundos = 8;

    @Column(name = "fl_whatsapp_simular_digitando", nullable = false)
    private Boolean whatsappSimularDigitando = true;

    @Column(name = "nu_whatsapp_limite_por_minuto")
    private Integer whatsappLimitePorMinuto = 20;

    @Column(name = "nu_whatsapp_limite_por_dia")
    private Integer whatsappLimitePorDia = 1000;

    @Column(name = "ds_whatsapp_modo_envio")
    private String whatsappModoEnvio = "FILA";

    @Column(name = "fl_exigir_consentimento", nullable = false)
    private Boolean exigirConsentimento = true;

    @Column(name = "fl_consentimento_expira", nullable = false)
    private Boolean consentimentoExpira = false;

    @Column(name = "nu_dias_expiracao_consentimento")
    private Integer diasExpiracaoConsentimento;

    @Column(name = "fl_bloqueio_automatico", nullable = false)
    private Boolean bloqueioAutomatico = true;

    @Column(name = "nu_limite_falhas_para_bloqueio")
    private Integer limiteFalhasParaBloqueio = 5;

    @Column(name = "fl_templates_versionamento", nullable = false)
    private Boolean templatesVersionamento = true;

    @Column(name = "fl_templates_exigir_aprovacao", nullable = false)
    private Boolean templatesExigirAprovacao = false;

    @Column(name = "fl_templates_validar_variaveis", nullable = false)
    private Boolean templatesValidarVariaveis = true;

    @Column(name = "fl_retry_automatico", nullable = false)
    private Boolean retryAutomatico = true;

    @Column(name = "nu_retry_tentativas")
    private Integer retryTentativas = 3;

    @Column(name = "nu_retry_intervalo_segundos")
    private Integer retryIntervaloSegundos = 60;

    @Column(name = "ds_prioridade_padrao")
    private String prioridadePadrao = "NORMAL";

    @Column(name = "nu_expiracao_fila_horas")
    private Integer expiracaoFilaHoras = 24;

    @Column(name = "fl_auditoria_habilitada", nullable = false)
    private Boolean auditoriaHabilitada = true;

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
