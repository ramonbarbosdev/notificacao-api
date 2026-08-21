package com.notificacao_api.model;

import java.time.LocalDateTime;

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
@Table(name = "whatsapp_configuracao")
public class WhatsappConfiguracao {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_whatsapp_configuracao")
    @SequenceGenerator(
            name = "seq_whatsapp_configuracao",
            sequenceName = "seq_whatsapp_configuracao",
            allocationSize = 1)
    @Column(name = "id_configuracao")
    private Long idConfiguracao;

    @Column(name = "id_organizacao", nullable = false)
    private Long idOrganizacao;

    @Enumerated(EnumType.STRING)
    @Column(name = "tp_provider", nullable = false, length = 30)
    private WhatsappProvedorEnvio provider = WhatsappProvedorEnvio.META_CLOUD;

    @Column(name = "ds_phone_number_id", nullable = false, length = 64)
    private String phoneNumberId;

    @Column(name = "ds_waba_id", length = 64)
    private String wabaId;

    @Column(name = "ds_api_version", nullable = false, length = 16)
    private String apiVersion = "v21.0";

    @Column(name = "fl_ativo", nullable = false)
    private Boolean ativo = true;

    @Column(name = "dt_ultimo_teste")
    private LocalDateTime dtUltimoTeste;

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
