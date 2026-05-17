package com.notificacao_api.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.notificacao_api.model.OrganizacaoWebhook;

public interface OrganizacaoWebhookRepository extends JpaRepository<OrganizacaoWebhook, Long> {

    List<OrganizacaoWebhook> findByIdOrganizacaoOrderByDtCriacaoDesc(Long idOrganizacao);
}
