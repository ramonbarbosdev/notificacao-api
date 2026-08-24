package com.notificacao_api.service.whatsapp;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.notificacao_api.dto.whatsapp.WhatsappConversaResponse;
import com.notificacao_api.dto.whatsapp.WhatsappInboundRequest;
import com.notificacao_api.enums.CanalNotificacao;
import com.notificacao_api.enums.WhatsappMensagemDirecao;
import com.notificacao_api.enums.WhatsappMensagemStatus;
import com.notificacao_api.enums.WhatsappMensagemTipo;
import com.notificacao_api.enums.WhatsappProvedorEnvio;
import com.notificacao_api.model.WhatsappMensagem;
import com.notificacao_api.repository.WhatsappMensagemRepository;
import com.notificacao_api.shared.TelefoneBrasilUtil;

@Service
public class WhatsappInboundService {

    private static final Logger log = LoggerFactory.getLogger(WhatsappInboundService.class);

    private final WhatsappMensagemRepository mensagemRepository;
    private final WhatsappConversaService conversaService;

    public WhatsappInboundService(
            WhatsappMensagemRepository mensagemRepository,
            WhatsappConversaService conversaService) {
        this.mensagemRepository = mensagemRepository;
        this.conversaService = conversaService;
    }

    @Transactional
    public Optional<WhatsappConversaResponse> processar(WhatsappInboundRequest request) {
        String telefone = TelefoneBrasilUtil.normalizarDestino(CanalNotificacao.WHATSAPP, request.telefone());

        if (!TelefoneBrasilUtil.celularBrasilComNonoDigito(telefone)) {
            log.warn(
                    "Inbound ignorado: telefone invalido org={} telefone={} jid={}",
                    request.idOrganizacao(),
                    request.telefone(),
                    request.jid());
            return Optional.empty();
        }

        if (StringUtils.hasText(request.idMensagemExterna())) {
            boolean duplicada = mensagemRepository
                    .findByIdOrganizacaoAndIdExterno(request.idOrganizacao(), request.idMensagemExterna())
                    .isPresent();
            if (duplicada) {
                return Optional.of(conversaService.buscarPorTelefone(request.idOrganizacao(), telefone));
            }
        }

        WhatsappMensagem mensagem = new WhatsappMensagem();
        mensagem.setIdOrganizacao(request.idOrganizacao());
        mensagem.setProvider(WhatsappProvedorEnvio.WHATSAPP_GATEWAY);
        mensagem.setTelefone(telefone);
        mensagem.setDirecao(WhatsappMensagemDirecao.INBOUND);
        mensagem.setTipo(mapearTipo(request.tipo()));
        mensagem.setConteudo(request.preview());
        mensagem.setIdExterno(request.idMensagemExterna());
        mensagem.setStatus(WhatsappMensagemStatus.DELIVERED);
        mensagemRepository.save(mensagem);

        WhatsappInboundRequest normalizado = new WhatsappInboundRequest(
                request.idOrganizacao(),
                telefone,
                request.jid(),
                request.idMensagemExterna(),
                request.tipo(),
                request.preview(),
                request.nmContato(),
                request.recebidaEm());

        return Optional.of(conversaService.registrarInbound(normalizado));
    }

    private WhatsappMensagemTipo mapearTipo(String tipo) {
        if (!StringUtils.hasText(tipo)) {
            return WhatsappMensagemTipo.TEXT;
        }

        return switch (tipo.toLowerCase()) {
            case "imagem", "image" -> WhatsappMensagemTipo.IMAGE;
            case "documento", "document" -> WhatsappMensagemTipo.DOCUMENT;
            default -> WhatsappMensagemTipo.TEXT;
        };
    }
}
