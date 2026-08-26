package com.notificacao_api.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.notificacao_api.model.PagamentoWebhookProcessado;

public interface PagamentoWebhookProcessadoRepository extends JpaRepository<PagamentoWebhookProcessado, Long> {

    boolean existsByIdEventoAsaas(String idEventoAsaas);
}
