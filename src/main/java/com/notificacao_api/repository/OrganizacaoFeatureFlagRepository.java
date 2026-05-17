package com.notificacao_api.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.notificacao_api.enums.RecursoFeature;
import com.notificacao_api.model.OrganizacaoFeatureFlag;

public interface OrganizacaoFeatureFlagRepository extends JpaRepository<OrganizacaoFeatureFlag, Long> {

    List<OrganizacaoFeatureFlag> findByIdOrganizacaoOrderByRecursoAsc(Long idOrganizacao);

    Optional<OrganizacaoFeatureFlag> findByIdOrganizacaoAndRecurso(Long idOrganizacao, RecursoFeature recurso);
}
