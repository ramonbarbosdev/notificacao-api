package com.notificacao_api.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.notificacao_api.model.OrganizacaoAssinatura;

public interface OrganizacaoAssinaturaRepository extends JpaRepository<OrganizacaoAssinatura, Long> {

    Optional<OrganizacaoAssinatura> findByIdOrganizacao(Long idOrganizacao);

    Optional<OrganizacaoAssinatura> findByIdAssinaturaAsaas(String idAssinaturaAsaas);
}
