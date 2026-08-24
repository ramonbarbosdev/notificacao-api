package com.notificacao_api.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.notificacao_api.dto.whatsapp.WhatsappConversaResponse;
import com.notificacao_api.service.whatsapp.WhatsappConversaService;

@RestController
@RequestMapping("/app/whatsapp/conversas")
public class WhatsappConversaController {

    private final WhatsappConversaService conversaService;

    public WhatsappConversaController(WhatsappConversaService conversaService) {
        this.conversaService = conversaService;
    }

    @GetMapping
    public ResponseEntity<List<WhatsappConversaResponse>> listar() {
        return ResponseEntity.ok(conversaService.listar());
    }

    @PostMapping("/{telefone}/liberar")
    public WhatsappConversaResponse liberar(@PathVariable String telefone) {
        return conversaService.liberar(telefone);
    }

    @PatchMapping("/{telefone}/marcar-lida")
    public WhatsappConversaResponse marcarComoLida(@PathVariable String telefone) {
        return conversaService.marcarComoLida(telefone);
    }

    @DeleteMapping("/{telefone}")
    public ResponseEntity<Void> excluir(@PathVariable String telefone) {
        conversaService.excluir(telefone);
        return ResponseEntity.noContent().build();
    }
}
