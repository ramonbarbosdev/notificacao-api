package com.notificacao_api.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.notificacao_api.enums.CanalNotificacao;
import com.notificacao_api.model.Contato;

public interface ContatoRepository extends JpaRepository<Contato, Long> {

    Optional<Contato> findByIdOrganizacaoAndCanalAndDestinatario(
            Long idOrganizacao,
            CanalNotificacao canal,
            String destinatario);
}
