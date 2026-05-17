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
@Table(name = "organizacao_api_key")
public class OrganizacaoApiKey {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_organizacao_api_key")
    @SequenceGenerator(name = "seq_organizacao_api_key", sequenceName = "seq_organizacao_api_key", allocationSize = 1)
    @Column(name = "id_api_key")
    private Long idApiKey;

    @Column(name = "id_organizacao", nullable = false)
    private Long idOrganizacao;

    @Column(name = "nm_api_key", nullable = false)
    private String nome;

    @Column(name = "ds_prefixo", nullable = false, length = 20)
    private String prefixo;

    @Column(name = "ds_hash_chave", nullable = false)
    private String hashChave;

    @Column(name = "ds_scopes", nullable = false, columnDefinition = "text")
    private String scopes;

    @Column(name = "fl_ativo", nullable = false)
    private Boolean ativo = true;

    @Column(name = "dt_ultimo_uso")
    private LocalDateTime ultimoUsoEm;

    @Column(name = "dt_expira")
    private LocalDateTime expiraEm;

    @Column(name = "dt_criacao", nullable = false, updatable = false)
    private LocalDateTime dtCriacao;

    @Column(name = "dt_revogacao")
    private LocalDateTime dtRevogacao;

    @PrePersist
    void prePersist() {
        dtCriacao = LocalDateTime.now();
    }
}
