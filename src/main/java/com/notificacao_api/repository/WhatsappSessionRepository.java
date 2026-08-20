package com.notificacao_api.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.notificacao_api.model.WhatsappSession;

public interface WhatsappSessionRepository extends JpaRepository<WhatsappSession, Long> {

    Optional<WhatsappSession> findByIdOrganizacao(Long idOrganizacao);

    List<WhatsappSession> findByIdOrganizacaoIn(Collection<Long> idsOrganizacao);

    void deleteByIdOrganizacao(Long idOrganizacao);
}
