package com.notificacao_api.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.notificacao_api.model.OrganizacaoApiKey;

public interface OrganizacaoApiKeyRepository extends JpaRepository<OrganizacaoApiKey, Long> {

    List<OrganizacaoApiKey> findByIdOrganizacaoOrderByDtCriacaoDesc(Long idOrganizacao);

    Optional<OrganizacaoApiKey> findByPrefixoAndAtivoTrue(String prefixo);
}
