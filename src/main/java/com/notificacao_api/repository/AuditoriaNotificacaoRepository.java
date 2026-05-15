package com.notificacao_api.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.notificacao_api.model.AuditoriaNotificacao;

public interface AuditoriaNotificacaoRepository extends JpaRepository<AuditoriaNotificacao, Long> {
}
