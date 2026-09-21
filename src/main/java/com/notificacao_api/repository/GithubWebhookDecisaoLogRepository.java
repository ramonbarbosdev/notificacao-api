package com.notificacao_api.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.notificacao_api.model.GithubWebhookDecisaoLog;

public interface GithubWebhookDecisaoLogRepository extends JpaRepository<GithubWebhookDecisaoLog, Long> {

    Page<GithubWebhookDecisaoLog> findByIdOrganizacaoOrderByDtCriacaoDesc(Long idOrganizacao, Pageable pageable);
}
