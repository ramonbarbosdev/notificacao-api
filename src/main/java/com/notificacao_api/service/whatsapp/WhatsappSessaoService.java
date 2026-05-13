package com.notificacao_api.service.whatsapp;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.notificacao_api.dto.whatsapp.EnviarMensagemWhatsappRequisicao;
import com.notificacao_api.dto.whatsapp.EnviarMensagemWhatsappResposta;
import com.notificacao_api.dto.whatsapp.StatusWhatsappResposta;
import com.notificacao_api.enums.WhatsappSessionStatus;
import com.notificacao_api.model.WhatsappSession;
import com.notificacao_api.repository.WhatsappSessionRepository;
import com.notificacao_api.service.TenantContextService;

@Service
public class WhatsappSessaoService {

    private final TenantContextService tenantContextService;
    private final WhatsAppGatewayClient gatewayClient;
    private final WhatsappSessionRepository whatsappSessionRepository;

    public WhatsappSessaoService(
            TenantContextService tenantContextService,
            WhatsAppGatewayClient gatewayClient,
            WhatsappSessionRepository whatsappSessionRepository) {
        this.tenantContextService = tenantContextService;
        this.gatewayClient = gatewayClient;
        this.whatsappSessionRepository = whatsappSessionRepository;
    }

    @Transactional
    public StatusWhatsappResposta conectar() {
        Long idOrganizacao = tenantContextService.idOrganizacaoObrigatoria();
        StatusWhatsappResposta resposta = gatewayClient.conectar(idOrganizacao);
        salvarStatus(idOrganizacao, resposta);
        return resposta;
    }

    @Transactional
    public StatusWhatsappResposta obterStatus() {
        Long idOrganizacao = tenantContextService.idOrganizacaoObrigatoria();
        StatusWhatsappResposta resposta = gatewayClient.obterStatus(idOrganizacao);
        salvarStatus(idOrganizacao, resposta);
        return resposta;
    }

    public EnviarMensagemWhatsappResposta enviarMensagem(EnviarMensagemWhatsappRequisicao requisicao) {
        Long idOrganizacao = tenantContextService.idOrganizacaoObrigatoria();
        return enviarMensagemDaOrganizacao(idOrganizacao, requisicao);
    }

    public EnviarMensagemWhatsappResposta enviarMensagemDaOrganizacao(
            Long idOrganizacao,
            EnviarMensagemWhatsappRequisicao requisicao) {
        return gatewayClient.enviarMensagem(idOrganizacao, requisicao);
    }

    @Transactional
    public StatusWhatsappResposta desconectar() {
        Long idOrganizacao = tenantContextService.idOrganizacaoObrigatoria();
        StatusWhatsappResposta resposta = gatewayClient.desconectar(idOrganizacao);
        salvarStatus(idOrganizacao, resposta);
        return resposta;
    }

    private WhatsappSession salvarStatus(Long idOrganizacao, StatusWhatsappResposta resposta) {
        WhatsappSession sessao = whatsappSessionRepository.findByIdOrganizacao(idOrganizacao)
                .orElseGet(() -> novaSessao(idOrganizacao));

        sessao.setTpStatus(statusDaResposta(resposta));
        sessao.setNuTelefone(resposta == null ? null : resposta.telefone());
        sessao.setDsSessionPath("organizacao-" + idOrganizacao);

        if (Boolean.TRUE.equals(resposta == null ? null : resposta.conectado())) {
            sessao.setDtUltimaConexao(LocalDateTime.now());
        }

        return whatsappSessionRepository.save(sessao);
    }

    private WhatsappSession novaSessao(Long idOrganizacao) {
        WhatsappSession sessao = new WhatsappSession();
        sessao.setIdOrganizacao(idOrganizacao);
        sessao.setTpStatus(WhatsappSessionStatus.NAO_INICIADO);
        sessao.setDsSessionPath("organizacao-" + idOrganizacao);
        return sessao;
    }

    private WhatsappSessionStatus statusDaResposta(StatusWhatsappResposta resposta) {
        if (resposta == null || Boolean.FALSE.equals(resposta.sucesso())) {
            return WhatsappSessionStatus.ERRO;
        }

        if (resposta.status() == null || resposta.status().isBlank()) {
            return Boolean.TRUE.equals(resposta.conectado())
                    ? WhatsappSessionStatus.CONECTADO
                    : WhatsappSessionStatus.NAO_INICIADO;
        }

        try {
            return WhatsappSessionStatus.valueOf(resposta.status().trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return Boolean.TRUE.equals(resposta.conectado())
                    ? WhatsappSessionStatus.CONECTADO
                    : WhatsappSessionStatus.ERRO;
        }
    }
}
