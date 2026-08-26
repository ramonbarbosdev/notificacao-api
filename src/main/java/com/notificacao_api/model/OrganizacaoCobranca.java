package com.notificacao_api.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.notificacao_api.enums.StatusCobranca;

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
@Table(name = "organizacao_cobranca")
public class OrganizacaoCobranca {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_organizacao_cobranca")
    @SequenceGenerator(name = "seq_organizacao_cobranca", sequenceName = "seq_organizacao_cobranca", allocationSize = 1)
    @Column(name = "id_organizacao_cobranca")
    private Long idOrganizacaoCobranca;

    @Column(name = "id_organizacao", nullable = false)
    private Long idOrganizacao;

    @Column(name = "id_cobranca_asaas", nullable = false, length = 50)
    private String idCobrancaAsaas;

    @Column(name = "vl_cobranca", nullable = false)
    private BigDecimal vlCobranca;

    @Enumerated(EnumType.STRING)
    @Column(name = "ds_status", nullable = false, length = 30)
    private StatusCobranca status;

    @Column(name = "ds_pix_copia_cola", columnDefinition = "text")
    private String dsPixCopiaCola;

    @Column(name = "ds_pix_qr_base64", columnDefinition = "text")
    private String dsPixQrBase64;

    @Column(name = "dt_vencimento")
    private LocalDate dtVencimento;

    @Column(name = "dt_pagamento")
    private LocalDateTime dtPagamento;

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
