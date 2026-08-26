package com.notificacao_api.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.notificacao_api.enums.StatusCobranca;
import com.notificacao_api.model.OrganizacaoCobranca;

public interface OrganizacaoCobrancaRepository extends JpaRepository<OrganizacaoCobranca, Long> {

    List<OrganizacaoCobranca> findByIdOrganizacaoOrderByDtCriacaoDesc(Long idOrganizacao);

    List<OrganizacaoCobranca> findByIdOrganizacaoAndStatusOrderByDtCriacaoDesc(
            Long idOrganizacao,
            StatusCobranca status);

    Optional<OrganizacaoCobranca> findByIdCobrancaAsaas(String idCobrancaAsaas);
}
