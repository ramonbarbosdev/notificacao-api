package com.notificacao_api.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.notificacao_api.model.Organizacao;

public interface OrganizacaoRepository extends JpaRepository<Organizacao, Long> {

    List<Organizacao> findAllByOrderByNmOrganizacaoAsc();
}
