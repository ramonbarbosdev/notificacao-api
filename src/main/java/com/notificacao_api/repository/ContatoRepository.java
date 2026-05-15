package com.notificacao_api.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.notificacao_api.enums.CanalNotificacao;
import com.notificacao_api.model.Contato;

public interface ContatoRepository extends JpaRepository<Contato, Long>,    JpaSpecificationExecutor<Contato> {

    Optional<Contato> findByIdOrganizacaoAndCanalAndDestinatario(
            Long idOrganizacao,
            CanalNotificacao canal,
            String destinatario);

    List<Contato> findByIdOrganizacaoOrderByDestinatarioAsc(Long idOrganizacao);

}
