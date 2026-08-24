package com.notificacao_api.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.notificacao_api.model.WhatsappConversa;

public interface WhatsappConversaRepository extends JpaRepository<WhatsappConversa, Long> {

    List<WhatsappConversa> findByIdOrganizacaoOrderByDtUltimaMensagemDesc(Long idOrganizacao);

    Optional<WhatsappConversa> findByIdOrganizacaoAndTelefone(Long idOrganizacao, String telefone);

    List<WhatsappConversa> findAllByIdOrganizacaoAndTelefone(Long idOrganizacao, String telefone);

    Optional<WhatsappConversa> findByIdOrganizacaoAndJid(Long idOrganizacao, String jid);

    List<WhatsappConversa> findAllByIdOrganizacaoAndJid(Long idOrganizacao, String jid);
}
