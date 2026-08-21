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
@Table(name = "whatsapp_credencial")
public class WhatsappCredencial {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_whatsapp_credencial")
    @SequenceGenerator(
            name = "seq_whatsapp_credencial",
            sequenceName = "seq_whatsapp_credencial",
            allocationSize = 1)
    @Column(name = "id_credencial")
    private Long idCredencial;

    @Column(name = "id_configuracao", nullable = false, unique = true)
    private Long idConfiguracao;

    @Column(name = "ds_access_token_criptografado", nullable = false, columnDefinition = "text")
    private String accessTokenCriptografado;

    @Column(name = "dt_token_expira")
    private LocalDateTime dtTokenExpira;

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
