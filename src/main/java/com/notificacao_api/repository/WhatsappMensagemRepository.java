package com.notificacao_api.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.notificacao_api.model.WhatsappMensagem;

public interface WhatsappMensagemRepository extends JpaRepository<WhatsappMensagem, Long> {

    Optional<WhatsappMensagem> findByIdOrganizacaoAndIdExterno(Long idOrganizacao, String idExterno);

    Optional<WhatsappMensagem> findByIdExterno(String idExterno);
}
