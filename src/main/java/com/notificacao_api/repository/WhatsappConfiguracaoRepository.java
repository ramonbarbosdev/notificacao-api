package com.notificacao_api.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.notificacao_api.model.WhatsappConfiguracao;

public interface WhatsappConfiguracaoRepository extends JpaRepository<WhatsappConfiguracao, Long> {

    Optional<WhatsappConfiguracao> findByIdOrganizacao(Long idOrganizacao);

    Optional<WhatsappConfiguracao> findByPhoneNumberIdAndAtivoTrue(String phoneNumberId);
}
