package com.notificacao_api.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.notificacao_api.model.AuditoriaEvento;

public interface AuditoriaEventoRepository extends JpaRepository<AuditoriaEvento, Long> {

    Page<AuditoriaEvento> findByIdOrganizacao(Long idOrganizacao, Pageable pageable);
}
