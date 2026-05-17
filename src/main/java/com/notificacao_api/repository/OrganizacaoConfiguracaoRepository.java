package com.notificacao_api.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.notificacao_api.model.OrganizacaoConfiguracao;

public interface OrganizacaoConfiguracaoRepository extends JpaRepository<OrganizacaoConfiguracao, Long> {

    Optional<OrganizacaoConfiguracao> findByIdOrganizacao(Long idOrganizacao);
}
