package com.notificacao_api.model;

import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

@Getter
@Setter
@MappedSuperclass
public abstract class TenantEntity extends AuditableEntity {

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_organizacao", nullable = false)
    private Organizacao organizacao;

    @JsonProperty("idOrganizacao")
    public Long getIdOrganizacao() {
        return organizacao == null ? null : organizacao.getIdOrganizacao();
    }
}
