package com.notificacao_api.service.whatsapp;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import com.notificacao_api.dto.whatsapp.WhatsappConversaResponse;
import com.notificacao_api.dto.whatsapp.WhatsappInboundRequest;
import com.notificacao_api.enums.CanalNotificacao;
import com.notificacao_api.enums.WhatsappConversaStatus;
import com.notificacao_api.enums.WhatsappMensagemDirecao;
import com.notificacao_api.enums.WhatsappMensagemStatus;
import com.notificacao_api.enums.WhatsappMensagemTipo;
import com.notificacao_api.enums.WhatsappProvedorEnvio;
import com.notificacao_api.model.Contato;
import com.notificacao_api.model.WhatsappConversa;
import com.notificacao_api.model.WhatsappMensagem;
import com.notificacao_api.repository.ContatoRepository;
import com.notificacao_api.repository.WhatsappConversaRepository;
import com.notificacao_api.repository.WhatsappMensagemRepository;
import com.notificacao_api.service.ContatoService;
import com.notificacao_api.service.OrganizacaoConfiguracaoService;
import com.notificacao_api.service.TenantContextService;
import com.notificacao_api.shared.TelefoneBrasilUtil;

@Service
public class WhatsappConversaService {

    private final TenantContextService tenantContextService;
    private final WhatsappConversaRepository conversaRepository;
    private final ContatoRepository contatoRepository;
    private final ContatoService contatoService;
    private final OrganizacaoConfiguracaoService organizacaoConfiguracaoService;
    private final WhatsappConexaoWebSocketService webSocketService;

    public WhatsappConversaService(
            TenantContextService tenantContextService,
            WhatsappConversaRepository conversaRepository,
            ContatoRepository contatoRepository,
            ContatoService contatoService,
            OrganizacaoConfiguracaoService organizacaoConfiguracaoService,
            WhatsappConexaoWebSocketService webSocketService) {
        this.tenantContextService = tenantContextService;
        this.conversaRepository = conversaRepository;
        this.contatoRepository = contatoRepository;
        this.contatoService = contatoService;
        this.organizacaoConfiguracaoService = organizacaoConfiguracaoService;
        this.webSocketService = webSocketService;
    }

    @Transactional
    public List<WhatsappConversaResponse> listar() {
        Long idOrganizacao = tenantContextService.idOrganizacaoObrigatoria();
        reconciliarTelefonesInvalidos(idOrganizacao);
        return conversaRepository.findByIdOrganizacaoOrderByDtUltimaMensagemDesc(idOrganizacao).stream()
                .map(conversa -> toResponse(
                        idOrganizacao,
                        conversa,
                        buscarContato(idOrganizacao, conversa.getTelefone())))
                .toList();
    }

    @Transactional(readOnly = true)
    public WhatsappConversaResponse buscarPorTelefone(Long idOrganizacao, String telefone) {
        String destinatario = normalizarTelefone(telefone);
        WhatsappConversa conversa = conversaRepository
                .findByIdOrganizacaoAndTelefone(idOrganizacao, destinatario)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversa nao encontrada."));
        return toResponse(idOrganizacao, conversa, buscarContato(idOrganizacao, destinatario));
    }

    @Transactional
    public WhatsappConversaResponse liberar(String telefone) {
        Long idOrganizacao = tenantContextService.idOrganizacaoObrigatoria();
        if (!organizacaoConfiguracaoService.exigeConsentimento(idOrganizacao)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Consentimento nao e exigido para esta organizacao.");
        }
        String destinatario = normalizarTelefone(telefone);

        WhatsappConversa conversa = buscarConversaObrigatoria(idOrganizacao, destinatario);

        String nome = StringUtils.hasText(conversa.getNmContato()) ? conversa.getNmContato() : conversa.getTelefone();
        Contato contato = contatoService.autorizarOrganizacao(idOrganizacao, conversa.getTelefone(), nome);

        conversa.setNaoLida(false);
        conversaRepository.save(conversa);

        WhatsappConversaResponse resposta = toResponse(idOrganizacao, conversa, Optional.of(contato));
        webSocketService.publicarConversa(idOrganizacao, "CONVERSA_ATUALIZADA", resposta);
        return resposta;
    }

    @Transactional
    public WhatsappConversaResponse marcarComoLida(String telefone) {
        Long idOrganizacao = tenantContextService.idOrganizacaoObrigatoria();
        String destinatario = normalizarTelefone(telefone);

        WhatsappConversa conversa = buscarConversaObrigatoria(idOrganizacao, destinatario);

        conversa.setNaoLida(false);
        conversaRepository.save(conversa);

        return toResponse(idOrganizacao, conversa, buscarContato(idOrganizacao, conversa.getTelefone()));
    }

    @Transactional
    public void excluir(String telefone) {
        Long idOrganizacao = tenantContextService.idOrganizacaoObrigatoria();
        String destinatario = normalizarTelefone(telefone);
        WhatsappConversa conversa = buscarConversaObrigatoria(idOrganizacao, destinatario);

        WhatsappConversaResponse resposta = toResponse(
                idOrganizacao,
                conversa,
                buscarContato(idOrganizacao, conversa.getTelefone()));

        conversaRepository.delete(conversa);
        webSocketService.publicarConversa(idOrganizacao, "CONVERSA_EXCLUIDA", resposta);
    }

    @Transactional
    public WhatsappConversaResponse registrarInbound(WhatsappInboundRequest request) {
        Long idOrganizacao = request.idOrganizacao();
        String telefone = normalizarTelefone(request.telefone());

        if (!TelefoneBrasilUtil.celularBrasilComNonoDigito(telefone)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Telefone inbound invalido.");
        }

        String preview = request.preview() != null ? request.preview().trim() : null;
        String nome = StringUtils.hasText(request.nmContato()) ? request.nmContato().trim() : telefone;

        corrigirConversaPorJid(idOrganizacao, request.jid(), telefone);

        Contato contato = contatoService.registrarInboundPendente(idOrganizacao, telefone, nome);

        WhatsappConversa conversa = conversaRepository
                .findByIdOrganizacaoAndTelefone(idOrganizacao, telefone)
                .orElseGet(() -> {
                    WhatsappConversa nova = new WhatsappConversa();
                    nova.setIdOrganizacao(idOrganizacao);
                    nova.setTelefone(telefone);
                    return nova;
                });

        if (StringUtils.hasText(nome) && !telefone.equals(nome)) {
            conversa.setNmContato(nome);
        }
        conversa.setUltimaMensagem(preview);
        conversa.setTipoUltimaMensagem(request.tipo());
        if (StringUtils.hasText(request.jid())) {
            conversa.setJid(request.jid());
        }
        conversa.setNaoLida(true);
        conversa.setDtUltimaMensagem(parseRecebidaEm(request.recebidaEm()));

        conversa = conversaRepository.save(conversa);

        WhatsappConversaResponse resposta = toResponse(idOrganizacao, conversa, Optional.of(contato));
        webSocketService.publicarConversa(idOrganizacao, "MENSAGEM_RECEBIDA", resposta);
        return resposta;
    }

    WhatsappConversaResponse toResponse(
            Long idOrganizacao,
            WhatsappConversa conversa,
            Optional<Contato> contato) {
        boolean exigeConsentimento = organizacaoConfiguracaoService.exigeConsentimento(idOrganizacao);
        return new WhatsappConversaResponse(
                conversa.getIdConversa(),
                contato.map(Contato::getIdContato).orElse(null),
                resolverTelefoneExibicao(conversa, contato, idOrganizacao),
                resolverNome(conversa, contato),
                conversa.getUltimaMensagem(),
                conversa.getTipoUltimaMensagem(),
                resolverStatus(idOrganizacao, contato),
                conversa.getNaoLida(),
                conversa.getDtUltimaMensagem(),
                exigeConsentimento);
    }

    private Optional<Contato> buscarContato(Long idOrganizacao, String telefone) {
        return contatoRepository.findByOrganizacao_IdOrganizacaoAndCanalAndDestinatario(
                idOrganizacao,
                CanalNotificacao.WHATSAPP,
                telefone);
    }

    private WhatsappConversa buscarConversaObrigatoria(Long idOrganizacao, String telefone) {
        return conversaRepository
                .findByIdOrganizacaoAndTelefone(idOrganizacao, telefone)
                .or(() -> conversaRepository.findByIdOrganizacaoOrderByDtUltimaMensagemDesc(idOrganizacao).stream()
                        .filter(conversa -> telefone.equals(resolverTelefoneExibicao(
                                conversa,
                                buscarContato(idOrganizacao, conversa.getTelefone()),
                                idOrganizacao)))
                        .findFirst())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversa nao encontrada."));
    }

    private WhatsappConversaStatus resolverStatus(Long idOrganizacao, Optional<Contato> contato) {
        if (contato.isPresent() && Boolean.TRUE.equals(contato.get().getBloqueado())) {
            return WhatsappConversaStatus.BLOQUEADO;
        }

        if (!organizacaoConfiguracaoService.exigeConsentimento(idOrganizacao)) {
            return WhatsappConversaStatus.LIBERADO;
        }

        if (contato.isEmpty()) {
            return WhatsappConversaStatus.PENDENTE;
        }

        if (Boolean.TRUE.equals(contato.get().getConsentimento())) {
            return WhatsappConversaStatus.LIBERADO;
        }

        return WhatsappConversaStatus.PENDENTE;
    }

    private String resolverNome(WhatsappConversa conversa, Optional<Contato> contato) {
        if (contato.isPresent() && StringUtils.hasText(contato.get().getNmContato())) {
            return contato.get().getNmContato();
        }
        if (StringUtils.hasText(conversa.getNmContato())) {
            return conversa.getNmContato();
        }
        return conversa.getTelefone();
    }

    private String resolverTelefoneExibicao(
            WhatsappConversa conversa,
            Optional<Contato> contato,
            Long idOrganizacao) {
        if (TelefoneBrasilUtil.celularBrasilComNonoDigito(conversa.getTelefone())) {
            return conversa.getTelefone();
        }

        if (contato.isPresent()
                && TelefoneBrasilUtil.celularBrasilComNonoDigito(contato.get().getDestinatario())) {
            return contato.get().getDestinatario();
        }

        String telefoneCorreto = buscarTelefoneCorretoPorNome(idOrganizacao, conversa.getNmContato());
        if (telefoneCorreto != null) {
            return telefoneCorreto;
        }

        return conversa.getTelefone();
    }

    @Transactional
    void reconciliarTelefonesInvalidos(Long idOrganizacao) {
        List<WhatsappConversa> conversas = conversaRepository.findByIdOrganizacaoOrderByDtUltimaMensagemDesc(
                idOrganizacao);

        for (WhatsappConversa conversa : conversas) {
            if (TelefoneBrasilUtil.celularBrasilComNonoDigito(conversa.getTelefone())) {
                continue;
            }

            String telefoneCorreto = buscarTelefoneCorretoPorNome(idOrganizacao, conversa.getNmContato());
            if (telefoneCorreto != null) {
                aplicarCorrecaoTelefone(idOrganizacao, conversa, telefoneCorreto);
            }
        }
    }

    private String buscarTelefoneCorretoPorNome(Long idOrganizacao, String nmContato) {
        if (!StringUtils.hasText(nmContato)) {
            return null;
        }

        List<Contato> matches = contatoRepository.findByOrganizacao_IdOrganizacaoAndCanalAndNmContatoIgnoreCase(
                idOrganizacao,
                CanalNotificacao.WHATSAPP,
                nmContato.trim());

        List<Contato> validos = matches.stream()
                .filter(contato -> TelefoneBrasilUtil.celularBrasilComNonoDigito(contato.getDestinatario()))
                .toList();

        if (validos.size() != 1) {
            return null;
        }

        return validos.get(0).getDestinatario();
    }

    private void aplicarCorrecaoTelefone(Long idOrganizacao, WhatsappConversa conversa, String telefoneCorreto) {
        String telefoneErrado = conversa.getTelefone();
        if (telefoneErrado.equals(telefoneCorreto)) {
            return;
        }

        Optional<WhatsappConversa> existente = conversaRepository.findByIdOrganizacaoAndTelefone(
                idOrganizacao,
                telefoneCorreto);

        if (existente.isPresent() && !existente.get().getIdConversa().equals(conversa.getIdConversa())) {
            WhatsappConversa keeper = existente.get();
            if (conversa.getDtUltimaMensagem().isAfter(keeper.getDtUltimaMensagem())) {
                keeper.setUltimaMensagem(conversa.getUltimaMensagem());
                keeper.setTipoUltimaMensagem(conversa.getTipoUltimaMensagem());
                keeper.setDtUltimaMensagem(conversa.getDtUltimaMensagem());
                keeper.setNaoLida(conversa.getNaoLida());
                if (StringUtils.hasText(conversa.getNmContato())) {
                    keeper.setNmContato(conversa.getNmContato());
                }
                if (StringUtils.hasText(conversa.getJid())) {
                    keeper.setJid(conversa.getJid());
                }
            }
            conversaRepository.delete(conversa);
            conversaRepository.save(keeper);
            return;
        }

        conversa.setTelefone(telefoneCorreto);
        conversaRepository.save(conversa);

        contatoRepository
                .findByOrganizacao_IdOrganizacaoAndCanalAndDestinatario(
                        idOrganizacao,
                        CanalNotificacao.WHATSAPP,
                        telefoneErrado)
                .ifPresent(contatoErrado -> {
                    if (!telefoneErrado.equals(telefoneCorreto)) {
                        contatoRepository.delete(contatoErrado);
                    }
                });
    }

    private void corrigirConversaPorJid(Long idOrganizacao, String jid, String telefoneCorreto) {
        if (!StringUtils.hasText(jid)) {
            return;
        }

        conversaRepository.findByIdOrganizacaoAndJid(idOrganizacao, jid).ifPresent(conversa -> {
            if (TelefoneBrasilUtil.celularBrasilComNonoDigito(conversa.getTelefone())) {
                return;
            }
            aplicarCorrecaoTelefone(idOrganizacao, conversa, telefoneCorreto);
        });
    }

    private String normalizarTelefone(String telefone) {
        return TelefoneBrasilUtil.normalizarDestino(CanalNotificacao.WHATSAPP, telefone);
    }

    private LocalDateTime parseRecebidaEm(String recebidaEm) {
        if (!StringUtils.hasText(recebidaEm)) {
            return LocalDateTime.now();
        }
        try {
            return LocalDateTime.parse(recebidaEm);
        } catch (DateTimeParseException ex) {
            try {
                return java.time.Instant.parse(recebidaEm).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime();
            } catch (DateTimeParseException ignored) {
                return LocalDateTime.now();
            }
        }
    }
}
