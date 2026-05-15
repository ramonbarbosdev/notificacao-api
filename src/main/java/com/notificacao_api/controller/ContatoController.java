package com.notificacao_api.controller;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.notificacao_api.dto.ApiResponseDTO;
import com.notificacao_api.dto.contato.ContatoFilter;
import com.notificacao_api.dto.contato.ContatoRequestDTO;
import com.notificacao_api.dto.contato.ContatoResponseDTO;
import com.notificacao_api.model.Contato;
import com.notificacao_api.service.ContatoService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/app/contatos")
public class ContatoController {

    private final ContatoService contatoService;

    public ContatoController(ContatoService contatoService) {
        this.contatoService = contatoService;
    }

    @GetMapping
    public ResponseEntity<ApiResponseDTO<List<ContatoResponseDTO>>> listar(
            ContatoFilter filter,
            @PageableDefault(size = 5, sort = "dtCriacao") Pageable pageable) {

        Page<ContatoResponseDTO> page = contatoService.listar(filter, pageable);

        return ResponseEntity.ok()
                .header("X-Total-Count", String.valueOf(page.getTotalElements()))
                .header("X-Page", String.valueOf(page.getNumber()))
                .header("X-Page-Size", String.valueOf(page.getSize()))
                .header("X-Total-Pages", String.valueOf(page.getTotalPages()))
                .body(new ApiResponseDTO<>("Operacao realizada com sucesso", page.getContent()));
    }

    @PostMapping("/consentimento")
    public ContatoResponseDTO autorizar(@Valid @RequestBody ContatoRequestDTO request) {
        return contatoService.toResponse(contatoService.autorizar(request.canal(), request.destinatario()));
    }

    @PostMapping("/bloquear")
    public ContatoResponseDTO bloquear(@Valid @RequestBody ContatoRequestDTO request) {
        return contatoService.toResponse(contatoService.bloquear(
                request.canal(),
                request.destinatario(),
                request.motivo()));
    }

    @PostMapping("/sincronizar-whatsapp")
    public void sincronizarWhatsapp() {
        contatoService.sincronizarWhatsapp();
    }

}