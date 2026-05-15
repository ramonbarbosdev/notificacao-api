package com.notificacao_api.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.notificacao_api.enums.CanalNotificacao;
import com.notificacao_api.model.ConfiguracaoProvedorNotificacao;

public interface ConfiguracaoProvedorNotificacaoRepository extends JpaRepository<ConfiguracaoProvedorNotificacao, Long> {

    Optional<ConfiguracaoProvedorNotificacao> findFirstByIdOrganizacaoAndCanalAndAtivoTrue(
            Long idOrganizacao,
            CanalNotificacao canal);
}
