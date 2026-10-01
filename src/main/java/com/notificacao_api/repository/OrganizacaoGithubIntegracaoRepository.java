package com.notificacao_api.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.notificacao_api.model.github.OrganizacaoGithubIntegracao;

import jakarta.persistence.LockModeType;

public interface OrganizacaoGithubIntegracaoRepository extends JpaRepository<OrganizacaoGithubIntegracao, Long> {

    Optional<OrganizacaoGithubIntegracao> findByIdOrganizacao(Long idOrganizacao);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM OrganizacaoGithubIntegracao i WHERE i.idOrganizacao = :idOrganizacao")
    Optional<OrganizacaoGithubIntegracao> findByIdOrganizacaoForUpdate(@Param("idOrganizacao") Long idOrganizacao);
}
