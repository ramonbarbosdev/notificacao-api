package com.notificacao_api.controller;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.notificacao_api.dto.ApiResponseDTO;
import com.notificacao_api.dto.notificacao.AdminFilaNotificacaoResponseDTO;
import com.notificacao_api.dto.notificacao.AdminNotificacaoDetalheResponseDTO;
import com.notificacao_api.dto.notificacao.AdminNotificacaoFilaFilter;
import com.notificacao_api.dto.notificacao.CancelarNotificacaoLoteRequest;
import com.notificacao_api.dto.notificacao.CancelarNotificacaoLoteResponse;
import com.notificacao_api.dto.notificacao.CancelarNotificacaoRequest;
import com.notificacao_api.dto.notificacao.AdminResumoOperacionalResponseDTO;
import com.notificacao_api.dto.whatsapp.StatusWhatsappResposta;
import com.notificacao_api.service.queue.FilaNotificacaoService;
import com.notificacao_api.service.queue.SegurancaOperacionalWhatsappService;
import com.notificacao_api.service.whatsapp.WhatsAppGatewayClient;
import com.notificacao_api.service.whatsapp.WhatsappSessaoOperacionalService;

@RestController
@RequestMapping("/admin/notificacoes")
public class AdminNotificacaoController {

    private final FilaNotificacaoService filaNotificacaoService;
    private final SegurancaOperacionalWhatsappService segurancaOperacionalWhatsappService;
    private final WhatsappSessaoOperacionalService whatsappSessaoOperacionalService;
    private final WhatsAppGatewayClient whatsAppGatewayClient;

    public AdminNotificacaoController(
            FilaNotificacaoService filaNotificacaoService,
            SegurancaOperacionalWhatsappService segurancaOperacionalWhatsappService,
            WhatsappSessaoOperacionalService whatsappSessaoOperacionalService,
            WhatsAppGatewayClient whatsAppGatewayClient) {
        this.filaNotificacaoService = filaNotificacaoService;
        this.segurancaOperacionalWhatsappService = segurancaOperacionalWhatsappService;
        this.whatsappSessaoOperacionalService = whatsappSessaoOperacionalService;
        this.whatsAppGatewayClient = whatsAppGatewayClient;
    }

    @GetMapping("/resumo-operacional")
    public ResponseEntity<AdminResumoOperacionalResponseDTO> resumoOperacional() {
        return ResponseEntity.ok(filaNotificacaoService.resumoOperacionalGlobal());
    }

    @PostMapping("/cancelar-lote")
    public ResponseEntity<CancelarNotificacaoLoteResponse> cancelarLote(
            @RequestBody CancelarNotificacaoLoteRequest request) {
        return ResponseEntity.ok(filaNotificacaoService.cancelarLoteGlobal(request));
    }

    @GetMapping("/fila")
    public ResponseEntity<ApiResponseDTO<List<AdminFilaNotificacaoResponseDTO>>> listarFila(
            AdminNotificacaoFilaFilter filter,
            @PageableDefault(size = 20, sort = "dtCriacao", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<AdminFilaNotificacaoResponseDTO> page = filaNotificacaoService.listarFilaGlobal(filter, pageable);
        return ResponseEntity.ok()
                .header("X-Total-Count", String.valueOf(page.getTotalElements()))
                .header("X-Page", String.valueOf(page.getNumber()))
                .header("X-Page-Size", String.valueOf(page.getSize()))
                .header("X-Total-Pages", String.valueOf(page.getTotalPages()))
                .body(new ApiResponseDTO<>("Operacao realizada com sucesso", page.getContent()));
    }

    @GetMapping("/{idNotificacao}")
    public ResponseEntity<AdminNotificacaoDetalheResponseDTO> obterDetalhe(@PathVariable Long idNotificacao) {
        return ResponseEntity.ok(filaNotificacaoService.obterDetalheGlobal(idNotificacao));
    }

    @PostMapping("/{idNotificacao}/reenviar")
    public ResponseEntity<AdminNotificacaoDetalheResponseDTO> reenviar(@PathVariable Long idNotificacao) {
        return ResponseEntity.ok(filaNotificacaoService.reenviarManualGlobal(idNotificacao));
    }

    @PostMapping("/{idNotificacao}/cancelar")
    public ResponseEntity<AdminNotificacaoDetalheResponseDTO> cancelar(
            @PathVariable Long idNotificacao,
            @RequestBody(required = false) CancelarNotificacaoRequest request) {
        String motivo = request == null ? null : request.motivo();
        return ResponseEntity.ok(filaNotificacaoService.cancelarManualGlobal(idNotificacao, motivo));
    }

    @PostMapping("/organizacoes/{idOrganizacao}/whatsapp/reativar-operacao")
    public ResponseEntity<StatusWhatsappResposta> reativarWhatsapp(@PathVariable Long idOrganizacao) {
        segurancaOperacionalWhatsappService.reativarSessao(idOrganizacao);
        StatusWhatsappResposta resposta = whatsAppGatewayClient.obterStatus(idOrganizacao);
        return ResponseEntity.ok(whatsappSessaoOperacionalService.enriquecer(idOrganizacao, resposta));
    }
}
