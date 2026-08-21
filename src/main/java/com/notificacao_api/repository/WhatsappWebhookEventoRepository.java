package com.notificacao_api.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.notificacao_api.model.WhatsappWebhookEvento;

public interface WhatsappWebhookEventoRepository extends JpaRepository<WhatsappWebhookEvento, Long> {

    boolean existsByIdEventoMeta(String idEventoMeta);
}
