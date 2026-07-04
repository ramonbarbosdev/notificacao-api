package com.notificacao_api.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.notificacao_api.model.AlertaOperacional;

public interface AlertaOperacionalRepository extends JpaRepository<AlertaOperacional, Long> {

    Page<AlertaOperacional> findByIdOrganizacaoOrderByDtCriacaoDesc(Long idOrganizacao, Pageable pageable);

    Page<AlertaOperacional> findAllByOrderByDtCriacaoDesc(Pageable pageable);
}
