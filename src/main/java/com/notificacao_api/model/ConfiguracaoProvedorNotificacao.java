package com.notificacao_api.model;

import java.time.LocalDateTime;

import com.notificacao_api.enums.CanalNotificacao;

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
@Table(name = "notificacao_configuracao_provedor")
public class ConfiguracaoProvedorNotificacao {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_notificacao_configuracao_provedor")
    @SequenceGenerator(
            name = "seq_notificacao_configuracao_provedor",
            sequenceName = "seq_notificacao_configuracao_provedor",
            allocationSize = 1)
    @Column(name = "id_configuracao_provedor")
    private Long idConfiguracaoProvedor;

    @Column(name = "id_organizacao", nullable = false)
    private Long idOrganizacao;

    @Enumerated(EnumType.STRING)
    @Column(name = "tp_canal", nullable = false, length = 30)
    private CanalNotificacao canal;

    @Column(name = "nm_provedor", nullable = false)
    private String provedor;

    @Column(name = "fl_ativo", nullable = false)
    private Boolean ativo = true;

    @Column(name = "ds_configuracoes", columnDefinition = "text")
    private String configuracoes;

    @Column(name = "ds_credenciais", columnDefinition = "text")
    private String credenciais;

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
