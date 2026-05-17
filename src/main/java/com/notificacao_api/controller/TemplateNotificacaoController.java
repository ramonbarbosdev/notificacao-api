package com.notificacao_api.controller;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.notificacao_api.dto.ApiResponseDTO;
import com.notificacao_api.dto.notificacao.EnviarNotificacaoResposta;
import com.notificacao_api.dto.template.AtualizarTemplateNotificacaoRequestDTO;
import com.notificacao_api.dto.template.CriarTemplateNotificacaoRequestDTO;
import com.notificacao_api.dto.template.EnviarTemplateNotificacaoRequestDTO;
import com.notificacao_api.dto.template.RenderizarTemplateNotificacaoResponseDTO;
import com.notificacao_api.dto.template.TemplateNotificacaoResponseDTO;
import com.notificacao_api.dto.template.TestarTemplateNotificacaoRequestDTO;
import com.notificacao_api.service.TemplateNotificacaoService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/app/notificacoes/templates")
public class TemplateNotificacaoController {

    private final TemplateNotificacaoService templateService;

    public TemplateNotificacaoController(TemplateNotificacaoService templateService) {
        this.templateService = templateService;
    }

    @GetMapping
    public ResponseEntity<ApiResponseDTO<List<TemplateNotificacaoResponseDTO>>> listar(
            @PageableDefault(size = 10, sort = "dtCriacao", direction = Sort.Direction.DESC) Pageable pageable) {

        Page<TemplateNotificacaoResponseDTO> page = templateService.listar(pageable);

        return ResponseEntity.ok()
                .header("X-Total-Count", String.valueOf(page.getTotalElements()))
                .header("X-Page", String.valueOf(page.getNumber()))
                .header("X-Page-Size", String.valueOf(page.getSize()))
                .header("X-Total-Pages", String.valueOf(page.getTotalPages()))
                .body(new ApiResponseDTO<>("Operacao realizada com sucesso", page.getContent()));
    }

    @GetMapping("/{idModelo}")
    public ResponseEntity<TemplateNotificacaoResponseDTO> buscar(@PathVariable Long idModelo) {
        return ResponseEntity.ok(templateService.buscar(idModelo));
    }

    @PostMapping
    public ResponseEntity<TemplateNotificacaoResponseDTO> criar(
            @Valid @RequestBody CriarTemplateNotificacaoRequestDTO request) {
        return ResponseEntity.ok(templateService.criar(request));
    }

    @PutMapping("/{idModelo}")
    public ResponseEntity<TemplateNotificacaoResponseDTO> atualizar(
            @PathVariable Long idModelo,
            @Valid @RequestBody AtualizarTemplateNotificacaoRequestDTO request) {
        return ResponseEntity.ok(templateService.atualizar(idModelo, request));
    }

    @PatchMapping("/{idModelo}/ativar")
    public ResponseEntity<TemplateNotificacaoResponseDTO> ativar(@PathVariable Long idModelo) {
        return ResponseEntity.ok(templateService.alterarStatus(idModelo, true));
    }

    @PatchMapping("/{idModelo}/inativar")
    public ResponseEntity<TemplateNotificacaoResponseDTO> inativar(@PathVariable Long idModelo) {
        return ResponseEntity.ok(templateService.alterarStatus(idModelo, false));
    }

    @PostMapping("/{chave}/testar")
    public ResponseEntity<RenderizarTemplateNotificacaoResponseDTO> testar(
            @PathVariable String chave,
            @Valid @RequestBody TestarTemplateNotificacaoRequestDTO request) {
        return ResponseEntity.ok(templateService.testar(chave, request));
    }

    @PostMapping("/enviar")
    public ResponseEntity<EnviarNotificacaoResposta> enviar(
            @Valid @RequestBody EnviarTemplateNotificacaoRequestDTO request) {
        return ResponseEntity.ok(templateService.enviar(request));
    }
}
