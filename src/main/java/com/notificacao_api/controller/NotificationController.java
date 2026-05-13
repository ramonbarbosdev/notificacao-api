package com.notificacao_api.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.notificacao_api.dto.notification.EnviarNotificacaoRequisicao;
import com.notificacao_api.dto.notification.EnviarNotificacaoResposta;
import com.notificacao_api.service.NotificationService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/app/notificacoes")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @PostMapping("/enviar")
    public ResponseEntity<EnviarNotificacaoResposta> enviar(
            @Valid @RequestBody EnviarNotificacaoRequisicao requisicao) {
        return ResponseEntity.ok(notificationService.enviar(requisicao));
    }
}
