package com.notificacao_api.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.notificacao_api.model.Notification;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
}
