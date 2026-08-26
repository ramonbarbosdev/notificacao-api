package com.notificacao_api.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.notificacao_api.enums.FormaPagamentoAssinatura;
import com.notificacao_api.enums.StatusAssinatura;

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
@Table(name = "organizacao_assinatura")
public class OrganizacaoAssinatura {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_organizacao_assinatura")
    @SequenceGenerator(name = "seq_organizacao_assinatura", sequenceName = "seq_organizacao_assinatura", allocationSize = 1)
    @Column(name = "id_organizacao_assinatura")
    private Long idOrganizacaoAssinatura;

    @Column(name = "id_organizacao", nullable = false, unique = true)
    private Long idOrganizacao;

    @Column(name = "id_plano", nullable = false)
    private Long idPlano;

    @Enumerated(EnumType.STRING)
    @Column(name = "ds_status", nullable = false, length = 30)
    private StatusAssinatura status;

    @Enumerated(EnumType.STRING)
    @Column(name = "ds_forma_pagamento", length = 20)
    private FormaPagamentoAssinatura formaPagamento;

    @Column(name = "id_assinatura_asaas", length = 50)
    private String idAssinaturaAsaas;

    @Column(name = "dt_proximo_vencimento")
    private LocalDate dtProximoVencimento;

    @Column(name = "dt_fim_trial")
    private LocalDateTime dtFimTrial;

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
