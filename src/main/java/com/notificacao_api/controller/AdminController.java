package com.notificacao_api.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.notificacao_api.dto.admin.AtualizarOrgGatewayRequestDTO;
import com.notificacao_api.dto.admin.CriarOrganizacaoRequestDTO;
import com.notificacao_api.dto.admin.CriarUsuarioOrganizacaoRequestDTO;
import com.notificacao_api.dto.admin.OrganizacaoResponseDTO;
import com.notificacao_api.dto.admin.UsuarioOrganizacaoResponseDTO;
import com.notificacao_api.dto.whatsapp.GatewaySessoesListaResponseDTO;
import com.notificacao_api.dto.whatsapp.StatusWhatsappResposta;
import com.notificacao_api.service.AdminService;
import com.notificacao_api.service.whatsapp.WhatsappSessaoService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/admin")
public class AdminController {

    private final AdminService adminService;
    private final WhatsappSessaoService whatsappSessaoService;

    public AdminController(AdminService adminService, WhatsappSessaoService whatsappSessaoService) {
        this.adminService = adminService;
        this.whatsappSessaoService = whatsappSessaoService;
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

    @DeleteMapping("/organizacoes/{idOrganizacao}")
    public ResponseEntity<OrganizacaoResponseDTO> inativarOrganizacao(@PathVariable Long idOrganizacao) {
        return ResponseEntity.ok(adminService.inativarOrganizacao(idOrganizacao));
    }

    @PatchMapping("/organizacoes/{idOrganizacao}/ativar")
    public ResponseEntity<OrganizacaoResponseDTO> ativarOrganizacao(@PathVariable Long idOrganizacao) {
        return ResponseEntity.ok(adminService.ativarOrganizacao(idOrganizacao));
    }

    @PostMapping("/organizacoes/{idOrganizacao}/whatsapp/atualizar-gateway")
    public ResponseEntity<StatusWhatsappResposta> atualizarOrganizacaoGateway(
            @PathVariable Long idOrganizacao,
            @RequestBody(required = false) AtualizarOrgGatewayRequestDTO request) {
        adminService.validarOrganizacaoExiste(idOrganizacao);
        Long idOrganizacaoAnterior = request == null ? null : request.idOrganizacaoAnterior();
        return ResponseEntity.ok(
                whatsappSessaoService.sincronizarGatewayOrganizacao(idOrganizacao, idOrganizacaoAnterior));
    }

    @GetMapping("/whatsapp/sessoes")
    public ResponseEntity<GatewaySessoesListaResponseDTO> listarSessoesGateway() {
        return ResponseEntity.ok(whatsappSessaoService.listarSessoesGateway());
    }

    @DeleteMapping("/organizacoes/{idOrganizacao}/permanente")
    public ResponseEntity<Void> excluirOrganizacaoPermanentemente(@PathVariable Long idOrganizacao) {
        adminService.excluirOrganizacaoPermanentemente(idOrganizacao);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/organizacoes/{idOrganizacao}/usuarios/{idUsuario}")
    public ResponseEntity<Void> inativarUsuarioDaOrganizacao(
            @PathVariable Long idOrganizacao,
            @PathVariable Long idUsuario) {
        adminService.inativarUsuarioDaOrganizacao(idOrganizacao, idUsuario);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/organizacoes/{idOrganizacao}/usuarios/{idUsuario}/ativar")
    public ResponseEntity<UsuarioOrganizacaoResponseDTO> ativarUsuarioDaOrganizacao(
            @PathVariable Long idOrganizacao,
            @PathVariable Long idUsuario) {
        return ResponseEntity.ok(adminService.ativarUsuarioDaOrganizacao(idOrganizacao, idUsuario));
    }

    @DeleteMapping("/organizacoes/{idOrganizacao}/usuarios/{idUsuario}/permanente")
    public ResponseEntity<Void> excluirUsuarioPermanentemente(
            @PathVariable Long idOrganizacao,
            @PathVariable Long idUsuario) {
        adminService.excluirUsuarioPermanentemente(idOrganizacao, idUsuario);
        return ResponseEntity.noContent().build();
    }
}
