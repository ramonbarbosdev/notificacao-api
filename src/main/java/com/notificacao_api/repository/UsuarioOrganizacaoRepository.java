package com.notificacao_api.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.notificacao_api.model.UsuarioOrganizacao;

public interface UsuarioOrganizacaoRepository extends JpaRepository<UsuarioOrganizacao, Long> {

    List<UsuarioOrganizacao> findByUsuarioIdUsuarioAndFlAtivoTrueAndOrganizacaoFlAtivoTrue(Long idUsuario);

    Optional<UsuarioOrganizacao> findByUsuarioIdUsuarioAndOrganizacaoIdOrganizacaoAndFlAtivoTrueAndOrganizacaoFlAtivoTrue(
            Long idUsuario,
            Long idOrganizacao);

    Optional<UsuarioOrganizacao> findByUsuarioIdUsuarioAndOrganizacaoIdOrganizacao(
            Long idUsuario,
            Long idOrganizacao);
}
