package com.notificacao_api.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.notificacao_api.dto.alerta.AlertaOperacionalResponse;
import com.notificacao_api.service.AlertaOperacionalService;

@RestController
@RequestMapping("/app/alertas-operacionais")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class AppAlertaOperacionalController {

    private final AlertaOperacionalService alertaOperacionalService;

    public AppAlertaOperacionalController(AlertaOperacionalService alertaOperacionalService) {
        this.alertaOperacionalService = alertaOperacionalService;
    }

    @GetMapping
    public ResponseEntity<Page<AlertaOperacionalResponse>> listar(
            @PageableDefault(size = 20, sort = "dtCriacao", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(alertaOperacionalService.listarDaOrganizacao(pageable));
    }
}
