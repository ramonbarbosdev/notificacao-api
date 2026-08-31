package com.notificacao_api.service.whatsapp;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.notificacao_api.dto.whatsapp.WhatsappConversaResponse;
import com.notificacao_api.dto.whatsapp.WhatsappInboundRequest;
import com.notificacao_api.enums.CanalNotificacao;
import com.notificacao_api.enums.WhatsappMensagemDirecao;
import com.notificacao_api.shared.TelefoneBrasilUtil;

@Service
public class WhatsappInboundService {

    private static final Logger log = LoggerFactory.getLogger(WhatsappInboundService.class);

    private final WhatsappConversaService conversaService;
    private final WhatsappInboundWebhookDispatcher webhookDispatcher;

    public WhatsappInboundService(
            WhatsappConversaService conversaService,
            WhatsappInboundWebhookDispatcher webhookDispatcher) {
        this.conversaService = conversaService;
        this.webhookDispatcher = webhookDispatcher;
    }

    @Transactional
    public Optional<WhatsappConversaResponse> processar(WhatsappInboundRequest request) {
        return processarMensagemSessao(request, false);
    }

    @Transactional
    public void processarLote(List<WhatsappInboundRequest> mensagens) {
        if (mensagens == null || mensagens.isEmpty()) {
            return;
        }

        for (WhatsappInboundRequest mensagem : mensagens) {
            processarMensagemSessao(mensagem, true);
        }
    }

    private Optional<WhatsappConversaResponse> processarMensagemSessao(
            WhatsappInboundRequest request,
            boolean silencioso) {
        String telefone = TelefoneBrasilUtil.normalizarDestino(CanalNotificacao.WHATSAPP, request.telefone());

        if (!TelefoneBrasilUtil.celularBrasilComNonoDigito(telefone)) {
            if (!silencioso) {
                log.warn(
                        "Mensagem ignorada: telefone invalido org={} telefone={} jid={}",
                        request.idOrganizacao(),
                        request.telefone(),
                        request.jid());
            }
            return Optional.empty();
        }

        WhatsappMensagemDirecao direcao = resolverDirecao(request.direcao());

        WhatsappInboundRequest normalizado = new WhatsappInboundRequest(
                request.idOrganizacao(),
                telefone,
                request.jid(),
                request.idMensagemExterna(),
                request.tipo(),
                request.preview(),
                request.nmContato(),
                request.recebidaEm(),
                direcao.name());

        if (direcao == WhatsappMensagemDirecao.OUTBOUND) {
            conversaService.registrarOutboundSessao(normalizado);
            return silencioso ? Optional.empty() : Optional.of(
                    conversaService.buscarPorTelefone(request.idOrganizacao(), telefone));
        }

        Optional<WhatsappConversaResponse> resposta = Optional.of(conversaService.registrarInbound(normalizado));
        webhookDispatcher.encaminhar(normalizado);
        return resposta;
    }

    private WhatsappMensagemDirecao resolverDirecao(String direcao) {
        if (direcao == null || direcao.isBlank()) {
            return WhatsappMensagemDirecao.INBOUND;
        }

        return "OUTBOUND".equalsIgnoreCase(direcao.trim())
                ? WhatsappMensagemDirecao.OUTBOUND
                : WhatsappMensagemDirecao.INBOUND;
    }
}
