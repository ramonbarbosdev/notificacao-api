package com.notificacao_api.model;

import java.time.LocalDateTime;

import com.notificacao_api.enums.TipoMetodoPagamento;

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
@Table(name = "organizacao_metodo_pagamento")
public class OrganizacaoMetodoPagamento {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_organizacao_metodo_pagamento")
    @SequenceGenerator(name = "seq_organizacao_metodo_pagamento", sequenceName = "seq_organizacao_metodo_pagamento", allocationSize = 1)
    @Column(name = "id_metodo_pagamento")
    private Long idMetodoPagamento;

    @Column(name = "id_organizacao", nullable = false)
    private Long idOrganizacao;

    @Enumerated(EnumType.STRING)
    @Column(name = "ds_tipo", nullable = false, length = 20)
    private TipoMetodoPagamento tipo;

    @Column(name = "id_cartao_asaas", nullable = false, length = 100)
    private String idCartaoAsaas;

    @Column(name = "nu_ultimos4", length = 4)
    private String nuUltimos4;

    @Column(name = "ds_bandeira", length = 30)
    private String dsBandeira;

    @Column(name = "fl_padrao", nullable = false)
    private Boolean flPadrao = false;

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
