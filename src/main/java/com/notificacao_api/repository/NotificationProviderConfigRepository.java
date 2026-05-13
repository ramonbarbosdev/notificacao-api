package com.notificacao_api.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.notificacao_api.enums.CanalNotificacao;
import com.notificacao_api.model.NotificationProviderConfig;

public interface NotificationProviderConfigRepository extends JpaRepository<NotificationProviderConfig, Long> {

    Optional<NotificationProviderConfig> findFirstByIdOrganizacaoAndCanalAndAtivoTrue(
            Long idOrganizacao,
            CanalNotificacao canal);
}
