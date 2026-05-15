package com.notificacao_api.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.notificacao_api.dto.notificacao.EnviarNotificacaoRequisicao;
import com.notificacao_api.dto.notificacao.EnviarNotificacaoResposta;
import com.notificacao_api.dto.notificacao.FilaNotificacaoResponseDTO;
import com.notificacao_api.service.NotificacaoService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/app/notificacoes")
public class NotificacaoController {

    private final NotificacaoService notificacaoService;

    public NotificacaoController(NotificacaoService notificacaoService) {
        this.notificacaoService = notificacaoService;
    }

    @PostMapping("/enviar")
    public ResponseEntity<EnviarNotificacaoResposta> enviar(
            @Valid @RequestBody EnviarNotificacaoRequisicao requisicao) {
        return ResponseEntity.ok(notificacaoService.enviar(requisicao));
    }

    @GetMapping("/fila")
    public ResponseEntity<FilaNotificacaoResponseDTO> listarFila() {
        return ResponseEntity.ok(notificacaoService.listarFila());
    }
}
