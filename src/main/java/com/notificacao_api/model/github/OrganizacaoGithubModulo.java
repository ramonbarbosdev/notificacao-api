package com.notificacao_api.model.github;

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
@Table(name = "organizacao_github_modulo")
public class OrganizacaoGithubModulo {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_organizacao_github_modulo")
    @SequenceGenerator(
            name = "seq_organizacao_github_modulo",
            sequenceName = "seq_organizacao_github_modulo",
            allocationSize = 1)
    @Column(name = "id_github_modulo")
    private Long idGithubModulo;

    @Column(name = "id_organizacao", nullable = false)
    private Long idOrganizacao;

    @Column(name = "ds_modulo", nullable = false, length = 40)
    private String dsModulo;

    @Column(name = "fl_habilitado", nullable = false)
    private Boolean flHabilitado = false;

    @Column(name = "ds_config_json", columnDefinition = "text")
    private String dsConfigJson;

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
