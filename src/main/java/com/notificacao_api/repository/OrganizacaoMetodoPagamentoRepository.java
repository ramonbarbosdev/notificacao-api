package com.notificacao_api.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.notificacao_api.model.OrganizacaoMetodoPagamento;

public interface OrganizacaoMetodoPagamentoRepository extends JpaRepository<OrganizacaoMetodoPagamento, Long> {

    List<OrganizacaoMetodoPagamento> findByIdOrganizacaoAndFlAtivoTrueOrderByFlPadraoDescDtCriacaoDesc(Long idOrganizacao);

    Optional<OrganizacaoMetodoPagamento> findByIdMetodoPagamentoAndIdOrganizacaoAndFlAtivoTrue(
            Long idMetodoPagamento,
            Long idOrganizacao);

    Optional<OrganizacaoMetodoPagamento> findByIdOrganizacaoAndFlPadraoTrueAndFlAtivoTrue(Long idOrganizacao);
}
