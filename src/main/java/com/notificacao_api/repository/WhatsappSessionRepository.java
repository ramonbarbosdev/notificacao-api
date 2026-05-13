package com.notificacao_api.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.notificacao_api.model.WhatsappSession;

public interface WhatsappSessionRepository extends JpaRepository<WhatsappSession, Long> {

    Optional<WhatsappSession> findByIdOrganizacao(Long idOrganizacao);
}
