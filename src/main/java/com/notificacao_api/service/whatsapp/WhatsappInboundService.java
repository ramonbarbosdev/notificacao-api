package com.notificacao_api.service.whatsapp;

import java.time.LocalDateTime;
import java.util.List;
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

        if (StringUtils.hasText(request.idMensagemExterna())) {
            boolean duplicada = mensagemRepository
                    .findByIdOrganizacaoAndIdExterno(request.idOrganizacao(), request.idMensagemExterna())
                    .isPresent();
            if (duplicada) {
                if (!silencioso) {
                    return Optional.of(conversaService.buscarPorTelefone(request.idOrganizacao(), telefone));
                }
                return Optional.empty();
            }
        }

        WhatsappMensagem mensagem = new WhatsappMensagem();
        mensagem.setIdOrganizacao(request.idOrganizacao());
        mensagem.setProvider(WhatsappProvedorEnvio.WHATSAPP_GATEWAY);
        mensagem.setTelefone(telefone);
        mensagem.setDirecao(direcao);
        mensagem.setTipo(mapearTipo(request.tipo()));
        mensagem.setConteudo(request.preview());
        mensagem.setIdExterno(request.idMensagemExterna());
        mensagem.setStatus(WhatsappMensagemStatus.DELIVERED);
        LocalDateTime recebidaEm = conversaService.parseRecebidaEmPublico(request.recebidaEm());
        mensagem.setDtEnvio(recebidaEm);
        mensagemRepository.save(mensagem);

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

        return Optional.of(conversaService.registrarInbound(normalizado));
    }

    private WhatsappMensagemDirecao resolverDirecao(String direcao) {
        if (!StringUtils.hasText(direcao)) {
            return WhatsappMensagemDirecao.INBOUND;
        }

        return "OUTBOUND".equalsIgnoreCase(direcao.trim())
                ? WhatsappMensagemDirecao.OUTBOUND
                : WhatsappMensagemDirecao.INBOUND;
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
