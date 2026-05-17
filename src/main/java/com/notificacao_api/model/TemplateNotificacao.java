package com.notificacao_api.model;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

import com.notificacao_api.enums.CanalNotificacao;
import com.notificacao_api.shared.StringSetJsonConverter;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(
        name = "notificacao_modelo",
        uniqueConstraints = @UniqueConstraint(
                name = "ux_notificacao_modelo_org_chave",
                columnNames = { "id_organizacao", "cd_chave" }))
public class TemplateNotificacao {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_notificacao_modelo")
    @SequenceGenerator(name = "seq_notificacao_modelo", sequenceName = "seq_notificacao_modelo", allocationSize = 1)
    @Column(name = "id_modelo")
    private Long idModelo;

    @Column(name = "id_organizacao", nullable = false)
    private Long idOrganizacao;

    @Enumerated(EnumType.STRING)
    @Column(name = "tp_canal", nullable = false, length = 30)
    private CanalNotificacao canal;

    @Column(name = "nm_modelo", nullable = false)
    private String nome;

    @Column(name = "cd_chave", nullable = false)
    private String chave;

    @Column(name = "ds_assunto")
    private String assunto;

    @Column(name = "ds_corpo", nullable = false, columnDefinition = "text")
    private String corpo;

    @Convert(converter = StringSetJsonConverter.class)
    @Column(name = "ds_variaveis_obrigatorias", nullable = false, columnDefinition = "text")
    private Set<String> variaveisObrigatorias = new LinkedHashSet<>();

    @Column(name = "nr_versao", nullable = false)
    private Integer versao = 1;

    @Column(name = "fl_ativo", nullable = false)
    private Boolean ativo = true;

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
