package com.notificacao_api.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.notificacao_api.model.github.OrganizacaoGithubModulo;

public interface OrganizacaoGithubModuloRepository extends JpaRepository<OrganizacaoGithubModulo, Long> {

    List<OrganizacaoGithubModulo> findByIdOrganizacao(Long idOrganizacao);

    Optional<OrganizacaoGithubModulo> findByIdOrganizacaoAndDsModulo(Long idOrganizacao, String dsModulo);
}
