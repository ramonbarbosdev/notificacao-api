package com.notificacao_api.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.notificacao_api.model.OrganizacaoGithubResponsavel;

public interface OrganizacaoGithubResponsavelRepository extends JpaRepository<OrganizacaoGithubResponsavel, Long> {

    Optional<OrganizacaoGithubResponsavel> findByIdOrganizacaoAndDsGithubLoginIgnoreCaseAndAtivoTrue(
            Long idOrganizacao,
            String dsGithubLogin);

    Optional<OrganizacaoGithubResponsavel> findByIdOrganizacaoAndDsGithubLoginIgnoreCase(
            Long idOrganizacao,
            String dsGithubLogin);

    Optional<OrganizacaoGithubResponsavel> findByIdOrganizacaoAndNuWhatsapp(Long idOrganizacao, String nuWhatsapp);

    Optional<OrganizacaoGithubResponsavel> findByIdOrganizacaoAndNuWhatsappAndDsGithubLoginIsNull(
            Long idOrganizacao,
            String nuWhatsapp);

    List<OrganizacaoGithubResponsavel> findByIdOrganizacaoOrderByDtAtualizacaoDesc(Long idOrganizacao);

    Optional<OrganizacaoGithubResponsavel> findByIdGithubResponsavelAndIdOrganizacao(
            Long idGithubResponsavel, Long idOrganizacao);
}
