package com.notificacao_api.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.notificacao_api.model.github.OrganizacaoGithubIntegracao;

public interface OrganizacaoGithubIntegracaoRepository extends JpaRepository<OrganizacaoGithubIntegracao, Long> {

    Optional<OrganizacaoGithubIntegracao> findByIdOrganizacao(Long idOrganizacao);
}
