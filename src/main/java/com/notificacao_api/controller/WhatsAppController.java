package com.notificacao_api.controller;

import com.notificacao_api.dto.whatsapp.EnviarMensagemWhatsappRequisicao;
import com.notificacao_api.dto.notificacao.EnviarNotificacaoRequisicao;
import com.notificacao_api.dto.notificacao.EnviarNotificacaoResposta;
import com.notificacao_api.dto.whatsapp.StatusWhatsappResposta;
import com.notificacao_api.dto.whatsapp.WhatsappDiagnosticoContatoResposta;
import com.notificacao_api.enums.CanalNotificacao;
import com.notificacao_api.dto.whatsapp.ProvisionarConfigWhatsappResposta;
import com.notificacao_api.service.ConfiguracaoProvedorNotificacaoService;
import com.notificacao_api.service.NotificacaoService;
import com.notificacao_api.service.TenantContextService;
import com.notificacao_api.service.whatsapp.WhatsappSessaoService;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/app/whatsapp")
public class WhatsAppController {

    private final WhatsappSessaoService whatsappSessaoService;
    private final NotificacaoService notificacaoService;
    private final ConfiguracaoProvedorNotificacaoService configuracaoProvedorNotificacaoService;
    private final TenantContextService tenantContextService;

    public WhatsAppController(
            WhatsappSessaoService whatsappSessaoService,
            NotificacaoService notificacaoService,
            ConfiguracaoProvedorNotificacaoService configuracaoProvedorNotificacaoService,
            TenantContextService tenantContextService) {
        this.whatsappSessaoService = whatsappSessaoService;
        this.notificacaoService = notificacaoService;
        this.configuracaoProvedorNotificacaoService = configuracaoProvedorNotificacaoService;
        this.tenantContextService = tenantContextService;
    }

    @PostMapping("/provisionar-config")
    public ProvisionarConfigWhatsappResposta provisionarConfig() {
        Long idOrganizacao = tenantContextService.idOrganizacaoObrigatoria();
        return configuracaoProvedorNotificacaoService.garantirWhatsappAtivo(idOrganizacao);
    }

    @PostMapping("/conectar")
    public StatusWhatsappResposta conectar() {
        return whatsappSessaoService.conectar();
    }

    @GetMapping("/status")
    public StatusWhatsappResposta obterStatus() {
        return whatsappSessaoService.obterStatus();
    }

    @GetMapping("/diagnostico")
    public WhatsappDiagnosticoContatoResposta diagnosticarContato(@RequestParam String telefone) {
        return whatsappSessaoService.diagnosticarContato(telefone);
    }

    @PostMapping("/enviar-mensagem")
    public EnviarNotificacaoResposta enviarMensagem(
            @Valid @RequestBody EnviarMensagemWhatsappRequisicao requisicao) {
        return notificacaoService.enviar(new EnviarNotificacaoRequisicao(
                CanalNotificacao.WHATSAPP,
                requisicao.telefone(),
                null,
                requisicao.mensagem()));
    }

    @PostMapping("/desconectar")
    public StatusWhatsappResposta desconectar() {
        return whatsappSessaoService.desconectar();
    }

    @PostMapping("/cancelar-conexao")
    public StatusWhatsappResposta cancelarConexao() {
        return whatsappSessaoService.desconectar();
    }

    @PostMapping("/reativar-operacao")
    public StatusWhatsappResposta reativarOperacao() {
        return whatsappSessaoService.reativarOperacao();
    }
}
