package com.notificacao_api.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.notificacao_api.model.ConfiguracaoGlobal;

public interface ConfiguracaoGlobalRepository extends JpaRepository<ConfiguracaoGlobal, Long> {
}
