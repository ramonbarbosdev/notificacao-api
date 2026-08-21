package com.notificacao_api.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.notificacao_api.model.WhatsappCredencial;

public interface WhatsappCredencialRepository extends JpaRepository<WhatsappCredencial, Long> {

    Optional<WhatsappCredencial> findByIdConfiguracao(Long idConfiguracao);
}
