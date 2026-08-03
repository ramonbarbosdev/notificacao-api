package com.notificacao_api.controller;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.notificacao_api.dto.ApiResponseDTO;
import com.notificacao_api.dto.notificacao.EnviarNotificacaoRequisicao;
import com.notificacao_api.dto.notificacao.EnviarNotificacaoResposta;
import com.notificacao_api.dto.notificacao.FilaNotificacaoResponseDTO;
import com.notificacao_api.dto.notificacao.FilaResumoResponseDTO;
import com.notificacao_api.dto.notificacao.NotificacaoFilaFilter;
import com.notificacao_api.dto.notificacao.StatusEnvioOrganizacaoResponse;
import com.notificacao_api.enums.CanalNotificacao;
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

    @GetMapping("/fila/resumo")
    public ResponseEntity<FilaResumoResponseDTO> resumoFila() {
        return ResponseEntity.ok(notificacaoService.resumoFila());
    }

    @GetMapping("/fila")
    public ResponseEntity<ApiResponseDTO<List<FilaNotificacaoResponseDTO>>> listar(
            NotificacaoFilaFilter filter,
            @PageableDefault(size = 5, sort = "dtCriacao", direction = Sort.Direction.DESC) Pageable pageable) {

        Page<FilaNotificacaoResponseDTO> page = notificacaoService.listarFila(filter, pageable);

        return ResponseEntity.ok()
                .header("X-Total-Count", String.valueOf(page.getTotalElements()))
                .header("X-Page", String.valueOf(page.getNumber()))
                .header("X-Page-Size", String.valueOf(page.getSize()))
                .header("X-Total-Pages", String.valueOf(page.getTotalPages()))
                .body(new ApiResponseDTO<>("Operacao realizada com sucesso", page.getContent()));
    }

    @GetMapping("/status-envio")
    public ResponseEntity<StatusEnvioOrganizacaoResponse> statusEnvio(
            @RequestParam(required = false) CanalNotificacao canal) {
        return ResponseEntity.ok(notificacaoService.consultarStatusEnvio(canal));
    }

    @PostMapping("/{idNotificacao}/reenviar")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<FilaNotificacaoResponseDTO> reenviar(@PathVariable Long idNotificacao) {
        return ResponseEntity.ok(notificacaoService.reenviarDaOrganizacao(idNotificacao));
    }
}
