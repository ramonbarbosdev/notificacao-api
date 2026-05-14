package com.notificacao_api.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.notificacao_api.dto.admin.CriarOrganizacaoRequestDTO;
import com.notificacao_api.dto.admin.CriarUsuarioOrganizacaoRequestDTO;
import com.notificacao_api.dto.admin.OrganizacaoResponseDTO;
import com.notificacao_api.dto.admin.UsuarioOrganizacaoResponseDTO;
import com.notificacao_api.service.AdminService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/admin")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, String>> statusSuperAdmin() {
        return ResponseEntity.ok(Map.of("message", "Acesso permitido para SUPER_ADMIN"));
    }

    @PostMapping("/organizacoes")
    public ResponseEntity<OrganizacaoResponseDTO> criarOrganizacao(
            @Valid @RequestBody CriarOrganizacaoRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(adminService.criarOrganizacao(request));
    }

    @GetMapping("/organizacoes")
    public ResponseEntity<List<OrganizacaoResponseDTO>> listarOrganizacoes() {
        return ResponseEntity.ok(adminService.listarOrganizacoes());
    }

    @PostMapping("/organizacoes/{idOrganizacao}/usuarios")
    public ResponseEntity<UsuarioOrganizacaoResponseDTO> criarUsuarioDaOrganizacao(
            @PathVariable Long idOrganizacao,
            @Valid @RequestBody CriarUsuarioOrganizacaoRequestDTO request) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(adminService.criarUsuarioDaOrganizacao(idOrganizacao, request));
    }

    @GetMapping("/organizacoes/{idOrganizacao}/usuarios")
    public ResponseEntity<List<UsuarioOrganizacaoResponseDTO>> listarUsuariosDaOrganizacao(
            @PathVariable Long idOrganizacao) {
        return ResponseEntity.ok(adminService.listarUsuariosDaOrganizacao(idOrganizacao));
    }


    @PutMapping("/organizacoes/{idOrganizacao}")
public ResponseEntity<OrganizacaoResponseDTO> editarOrganizacao(
        @PathVariable Long idOrganizacao,
        @Valid @RequestBody CriarOrganizacaoRequestDTO request) {

    return ResponseEntity.ok(
            adminService.editarOrganizacao(idOrganizacao, request));
}

@PutMapping("/organizacoes/{idOrganizacao}/usuarios/{idUsuario}")
public ResponseEntity<UsuarioOrganizacaoResponseDTO> editarUsuarioDaOrganizacao(
        @PathVariable Long idOrganizacao,
        @PathVariable Long idUsuario,
        @Valid @RequestBody CriarUsuarioOrganizacaoRequestDTO request) {

    return ResponseEntity.ok(
            adminService.editarUsuarioDaOrganizacao(
                    idOrganizacao,
                    idUsuario,
                    request));
}
}
