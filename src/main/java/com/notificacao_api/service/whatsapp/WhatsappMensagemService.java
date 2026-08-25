package com.notificacao_api.service.whatsapp;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.notificacao_api.dto.whatsapp.WhatsappInboundRequest;
import com.notificacao_api.dto.whatsapp.WhatsappMensagemResponse;
import com.notificacao_api.dto.whatsapp.WhatsappMensagemSessaoItemDTO;
import com.notificacao_api.enums.WhatsappMensagemDirecao;
import com.notificacao_api.enums.WhatsappMensagemStatus;
import com.notificacao_api.enums.WhatsappMensagemTipo;
import com.notificacao_api.enums.WhatsappProvedorEnvio;
import com.notificacao_api.model.WhatsappMensagem;
import com.notificacao_api.repository.WhatsappMensagemRepository;
import com.notificacao_api.service.whatsapp.provider.ResultadoEnvioWhatsapp;

@Service
public class WhatsappMensagemService {

    private final WhatsappMensagemRepository repository;

    public WhatsappMensagemService(WhatsappMensagemRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public WhatsappMensagem registrarEnvioOutbound(
            Long idOrganizacao,
            Long idNotificacao,
            String telefone,
            WhatsappMensagemTipo tipo,
            String templateName,
            ResultadoEnvioWhatsapp resultado) {
        WhatsappMensagem mensagem = new WhatsappMensagem();
        mensagem.setIdOrganizacao(idOrganizacao);
        mensagem.setIdNotificacao(idNotificacao);
        mensagem.setProvider(WhatsappProvedorEnvio.META_CLOUD);
        mensagem.setTelefone(telefone);
        mensagem.setDirecao(WhatsappMensagemDirecao.OUTBOUND);
        mensagem.setTipo(tipo);
        mensagem.setTemplateName(templateName);
        mensagem.setIdExterno(resultado.externalMessageId());
        mensagem.setStatus(resultado.status());
        mensagem.setErro(resultado.erro());
        if (resultado.confirmado() || resultado.externalMessageId() != null) {
            mensagem.setDtEnvio(LocalDateTime.now());
        }
        return repository.save(mensagem);
    }

    @Transactional
    public void atualizarStatusPorIdExterno(String idExterno, WhatsappMensagemStatus status, String codigoErro, String erro) {
        repository.findByIdExterno(idExterno).ifPresent(mensagem -> {
            mensagem.setStatus(status);
            mensagem.setCodigoErro(codigoErro);
            if (erro != null) {
                mensagem.setErro(erro);
            }
            LocalDateTime agora = LocalDateTime.now();
            switch (status) {
                case SENT -> mensagem.setDtEnvio(agora);
                case DELIVERED -> mensagem.setDtEntrega(agora);
                case READ -> mensagem.setDtLeitura(agora);
                case FAILED -> {
                    if (mensagem.getDtEnvio() == null) {
                        mensagem.setDtEnvio(agora);
                    }
                }
                default -> {
                }
            }
            repository.save(mensagem);
        });
    }

    @Transactional
    public WhatsappMensagem registrarMensagemGateway(WhatsappInboundRequest request) {
        if (request == null || request.idOrganizacao() == null || !StringUtils.hasText(request.telefone())) {
            return null;
        }

        if (StringUtils.hasText(request.idMensagemExterna())) {
            Optional<WhatsappMensagem> existente = repository.findByIdOrganizacaoAndIdExterno(
                    request.idOrganizacao(),
                    request.idMensagemExterna());
            if (existente.isPresent()) {
                return existente.get();
            }
        }

        WhatsappMensagem mensagem = new WhatsappMensagem();
        mensagem.setIdOrganizacao(request.idOrganizacao());
        mensagem.setProvider(WhatsappProvedorEnvio.WHATSAPP_GATEWAY);
        mensagem.setTelefone(request.telefone());
        mensagem.setDirecao(resolverDirecao(request.direcao()));
        mensagem.setTipo(mapearTipoGateway(request.tipo()));
        mensagem.setIdExterno(request.idMensagemExterna());
        mensagem.setConteudo(normalizarConteudo(request.preview()));
        mensagem.setStatus(WhatsappMensagemStatus.DELIVERED);
        mensagem.setDtEnvio(parseRecebidaEm(request.recebidaEm()));
        return repository.save(mensagem);
    }

    @Transactional
    public int registrarLoteGateway(List<WhatsappInboundRequest> mensagens) {
        if (mensagens == null || mensagens.isEmpty()) {
            return 0;
        }

        int importadas = 0;
        for (WhatsappInboundRequest mensagem : mensagens) {
            WhatsappMensagem salva = registrarMensagemGateway(mensagem);
            if (salva != null) {
                importadas += 1;
            }
        }
        return importadas;
    }

    @Transactional
    public WhatsappMensagem registrarOutboundGateway(
            Long idOrganizacao,
            Long idNotificacao,
            String telefone,
            String conteudo,
            String idExterno) {
        if (StringUtils.hasText(idExterno)) {
            Optional<WhatsappMensagem> existente = repository.findByIdOrganizacaoAndIdExterno(idOrganizacao, idExterno);
            if (existente.isPresent()) {
                return existente.get();
            }
        }

        WhatsappMensagem mensagem = new WhatsappMensagem();
        mensagem.setIdOrganizacao(idOrganizacao);
        mensagem.setIdNotificacao(idNotificacao);
        mensagem.setProvider(WhatsappProvedorEnvio.WHATSAPP_GATEWAY);
        mensagem.setTelefone(telefone);
        mensagem.setDirecao(WhatsappMensagemDirecao.OUTBOUND);
        mensagem.setTipo(WhatsappMensagemTipo.TEXT);
        mensagem.setConteudo(normalizarConteudo(conteudo));
        mensagem.setIdExterno(idExterno);
        mensagem.setStatus(WhatsappMensagemStatus.SENT);
        mensagem.setDtEnvio(LocalDateTime.now());
        return repository.save(mensagem);
    }

    @Transactional
    public void limparMensagensGateway(Long idOrganizacao) {
        if (idOrganizacao == null) {
            return;
        }

        repository.deleteByIdOrganizacaoAndProvider(idOrganizacao, WhatsappProvedorEnvio.WHATSAPP_GATEWAY);
    }

    @Transactional(readOnly = true)
    public Page<WhatsappMensagemResponse> listarRecentes(
            Long idOrganizacao,
            String telefone,
            WhatsappMensagemDirecao direcao,
            Pageable pageable) {
        Page<WhatsappMensagem> pagina = direcao != null
                ? repository.findByIdOrganizacaoAndTelefoneAndDirecaoOrderByDtEnvioDescDtCriacaoDesc(
                        idOrganizacao, telefone, direcao, pageable)
                : repository.findByIdOrganizacaoAndTelefoneOrderByDtEnvioDescDtCriacaoDesc(
                        idOrganizacao, telefone, pageable);

        List<WhatsappMensagemResponse> ordenadas = pagina.getContent().stream()
                .sorted(Comparator
                        .comparing(WhatsappMensagem::getDtEnvio, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(WhatsappMensagem::getDtCriacao, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(this::toResponse)
                .toList();

        return new PageImpl<>(ordenadas, pageable, pagina.getTotalElements());
    }

    @Transactional(readOnly = true)
    public List<WhatsappMensagemResponse> listarAnteriores(
            Long idOrganizacao,
            String telefone,
            WhatsappMensagemDirecao direcao,
            Long antesDeIdMensagem,
            int limite) {
        LocalDateTime antesDe = repository.findById(antesDeIdMensagem)
                .filter(mensagem -> idOrganizacao.equals(mensagem.getIdOrganizacao()))
                .map(WhatsappMensagem::getDtEnvio)
                .orElse(null);

        if (antesDe == null) {
            return List.of();
        }

        Pageable pageable = Pageable.ofSize(limite);
        List<WhatsappMensagem> mensagens = direcao != null
                ? repository.findByIdOrganizacaoAndTelefoneAndDirecaoAndDtEnvioLessThanOrderByDtEnvioDesc(
                        idOrganizacao, telefone, direcao, antesDe, pageable)
                : repository.findByIdOrganizacaoAndTelefoneAndDtEnvioLessThanOrderByDtEnvioDesc(
                        idOrganizacao, telefone, antesDe, pageable);

        List<WhatsappMensagemResponse> ordenadas = new ArrayList<>(mensagens.stream().map(this::toResponse).toList());
        ordenadas.sort(Comparator
                .comparing(WhatsappMensagemResponse::dtEnvio, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(WhatsappMensagemResponse::dtCriacao, Comparator.nullsLast(Comparator.naturalOrder())));
        return ordenadas;
    }

    @Transactional(readOnly = true)
    public boolean possuiHistorico(Long idOrganizacao, String telefone) {
        return repository.countByIdOrganizacaoAndTelefone(idOrganizacao, telefone) > 0;
    }

    @Transactional
    public int importarDoGateway(Long idOrganizacao, String telefone, List<WhatsappMensagemSessaoItemDTO> itens) {
        if (itens == null || itens.isEmpty()) {
            return 0;
        }

        int importadas = 0;
        for (WhatsappMensagemSessaoItemDTO item : itens) {
            if (item == null) {
                continue;
            }

            WhatsappInboundRequest request = new WhatsappInboundRequest(
                    idOrganizacao,
                    telefone,
                    null,
                    item.idMensagemExterna(),
                    item.tipo(),
                    item.preview(),
                    null,
                    item.enviadaEm(),
                    item.direcao());
            WhatsappMensagem salva = registrarMensagemGateway(request);
            if (salva != null) {
                importadas += 1;
            }
        }
        return importadas;
    }

    public WhatsappMensagemResponse toResponse(WhatsappMensagem mensagem) {
        return new WhatsappMensagemResponse(
                mensagem.getIdMensagem(),
                mensagem.getTelefone(),
                mensagem.getDirecao(),
                mensagem.getTipo(),
                mensagem.getConteudo(),
                mensagem.getStatus(),
                mensagem.getIdExterno(),
                mensagem.getDtEnvio(),
                mensagem.getDtCriacao());
    }

    private WhatsappMensagemDirecao resolverDirecao(String direcao) {
        return "OUTBOUND".equalsIgnoreCase(StringUtils.trimWhitespace(direcao))
                ? WhatsappMensagemDirecao.OUTBOUND
                : WhatsappMensagemDirecao.INBOUND;
    }

    private WhatsappMensagemTipo mapearTipoGateway(String tipo) {
        if (!StringUtils.hasText(tipo)) {
            return WhatsappMensagemTipo.TEXT;
        }

        return switch (tipo.trim().toLowerCase(Locale.ROOT)) {
            case "imagem", "image" -> WhatsappMensagemTipo.IMAGE;
            case "documento", "document" -> WhatsappMensagemTipo.DOCUMENT;
            case "template" -> WhatsappMensagemTipo.TEMPLATE;
            default -> WhatsappMensagemTipo.TEXT;
        };
    }

    private String normalizarConteudo(String conteudo) {
        if (!StringUtils.hasText(conteudo)) {
            return null;
        }
        return conteudo.trim();
    }

    private LocalDateTime parseRecebidaEm(String recebidaEm) {
        if (!StringUtils.hasText(recebidaEm)) {
            return LocalDateTime.now();
        }
        try {
            return Instant.parse(recebidaEm).atZone(ZoneId.systemDefault()).toLocalDateTime();
        } catch (DateTimeParseException ex) {
            try {
                return LocalDateTime.parse(recebidaEm);
            } catch (DateTimeParseException ignored) {
                return LocalDateTime.now();
            }
        }
    }
}
