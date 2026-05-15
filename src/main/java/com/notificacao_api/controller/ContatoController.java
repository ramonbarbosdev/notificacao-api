package com.notificacao_api.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

    @PostMapping("/consentimento")
    public ContatoResponseDTO autorizar(@Valid @RequestBody ContatoRequestDTO request) {
        return toResponse(contatoService.autorizar(request.canal(), request.destinatario()));
    }

    @PostMapping("/bloquear")
    public ContatoResponseDTO bloquear(@Valid @RequestBody ContatoRequestDTO request) {
        return toResponse(contatoService.bloquear(request.canal(), request.destinatario(), request.motivo()));
    }

    private ContatoResponseDTO toResponse(Contato contato) {
        return new ContatoResponseDTO(
                contato.getIdContato(),
                contato.getCanal(),
                contato.getDestinatario(),
                contato.getConsentimento(),
                contato.getBloqueado(),
                contato.getMotivoBloqueio(),
                contato.getDtConsentimento(),
                contato.getDtBloqueio());
    }
}
