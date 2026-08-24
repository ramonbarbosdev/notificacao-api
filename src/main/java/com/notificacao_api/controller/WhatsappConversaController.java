package com.notificacao_api.controller;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.notificacao_api.dto.ApiResponseDTO;
import com.notificacao_api.dto.whatsapp.WhatsappConversaFilter;
import com.notificacao_api.dto.whatsapp.WhatsappConversaResponse;
import com.notificacao_api.dto.whatsapp.WhatsappMensagemResponse;
import com.notificacao_api.service.whatsapp.WhatsappConversaService;

@RestController
@RequestMapping("/app/whatsapp/conversas")
public class WhatsappConversaController {

    private final WhatsappConversaService conversaService;

    public WhatsappConversaController(WhatsappConversaService conversaService) {
        this.conversaService = conversaService;
    }

    @GetMapping
    public ResponseEntity<ApiResponseDTO<List<WhatsappConversaResponse>>> listar(
            @ModelAttribute WhatsappConversaFilter filter,
            @PageableDefault(size = 10, sort = "dtUltimaMensagem", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<WhatsappConversaResponse> page = conversaService.listar(filter, pageable);

        return ResponseEntity.ok()
                .header("X-Total-Count", String.valueOf(page.getTotalElements()))
                .header("X-Page", String.valueOf(page.getNumber()))
                .header("X-Page-Size", String.valueOf(page.getSize()))
                .header("X-Total-Pages", String.valueOf(page.getTotalPages()))
                .body(new ApiResponseDTO<>("Operacao realizada com sucesso", page.getContent()));
    }

    @PostMapping("/{telefone}/liberar")
    public WhatsappConversaResponse liberar(@PathVariable String telefone) {
        return conversaService.liberar(telefone);
    }

    @PatchMapping("/{telefone}/marcar-lida")
    public WhatsappConversaResponse marcarComoLida(@PathVariable String telefone) {
        return conversaService.marcarComoLida(telefone);
    }

    @PostMapping("/{telefone}/sincronizar-inbox")
    public WhatsappConversaResponse sincronizarInbox(@PathVariable String telefone) {
        return conversaService.sincronizarInboxDaSessao(telefone);
    }

    @PostMapping("/{telefone}/sincronizar-historico")
    public WhatsappConversaResponse sincronizarHistorico(@PathVariable String telefone) {
        return conversaService.sincronizarHistoricoDaSessao(telefone);
    }

    @GetMapping("/{telefone}/mensagens")
    public ResponseEntity<ApiResponseDTO<List<WhatsappMensagemResponse>>> listarMensagens(
            @PathVariable String telefone,
            @PageableDefault(size = 50, sort = "dtCriacao", direction = Sort.Direction.ASC) Pageable pageable) {
        Page<WhatsappMensagemResponse> page = conversaService.listarMensagens(telefone, pageable);

        return ResponseEntity.ok()
                .header("X-Total-Count", String.valueOf(page.getTotalElements()))
                .header("X-Page", String.valueOf(page.getNumber()))
                .header("X-Page-Size", String.valueOf(page.getSize()))
                .header("X-Total-Pages", String.valueOf(page.getTotalPages()))
                .body(new ApiResponseDTO<>("Operacao realizada com sucesso", page.getContent()));
    }

    @DeleteMapping("/{telefone}")
    public ResponseEntity<Void> excluir(@PathVariable String telefone) {
        conversaService.excluir(telefone);
        return ResponseEntity.noContent().build();
    }
}
