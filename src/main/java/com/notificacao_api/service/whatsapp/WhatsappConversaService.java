package com.notificacao_api.service.whatsapp;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import com.notificacao_api.dto.whatsapp.WhatsappConversaFilter;
import com.notificacao_api.dto.whatsapp.WhatsappConversaOperacionalGatewayItemDTO;
import com.notificacao_api.dto.whatsapp.WhatsappConversaResponse;
import com.notificacao_api.dto.whatsapp.WhatsappConversasOperacionaisGatewayResposta;
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
    private final WhatsAppGatewayClient gatewayClient;

    public WhatsappConversaService(
            TenantContextService tenantContextService,
            WhatsappConversaRepository conversaRepository,
            ContatoRepository contatoRepository,
            ContatoService contatoService,
            OrganizacaoConfiguracaoService organizacaoConfiguracaoService,
            WhatsappConexaoWebSocketService webSocketService,
            WhatsAppGatewayClient gatewayClient) {
        this.tenantContextService = tenantContextService;
        this.conversaRepository = conversaRepository;
        this.contatoRepository = contatoRepository;
        this.contatoService = contatoService;
        this.organizacaoConfiguracaoService = organizacaoConfiguracaoService;
        this.webSocketService = webSocketService;
        this.gatewayClient = gatewayClient;
    }

    @Transactional
    public Page<WhatsappConversaResponse> listar(WhatsappConversaFilter filter, Pageable pageable) {
        Long idOrganizacao = tenantContextService.idOrganizacaoObrigatoria();
        List<WhatsappConversaResponse> todas = listarMescladas(idOrganizacao);
        List<WhatsappConversaResponse> filtradas = aplicarFiltro(todas, filter);

        int page = pageable.getPageNumber();
        int size = pageable.getPageSize();
        int inicio = page * size;

        if (inicio >= filtradas.size()) {
            return new PageImpl<>(List.of(), pageable, filtradas.size());
        }

        int fim = Math.min(inicio + size, filtradas.size());
        return new PageImpl<>(filtradas.subList(inicio, fim), pageable, filtradas.size());
    }

    private List<WhatsappConversaResponse> listarMescladas(Long idOrganizacao) {
        reconciliarTelefonesInvalidos(idOrganizacao);

        List<WhatsappConversa> conversasPersistidas =
                conversaRepository.findByIdOrganizacaoOrderByDtUltimaMensagemDesc(idOrganizacao);
        Map<String, WhatsappConversaOperacionalGatewayItemDTO> operacionais =
                carregarOperacionaisGateway(idOrganizacao);

        Map<String, WhatsappConversa> persistidasPorTelefone = new LinkedHashMap<>();
        for (WhatsappConversa conversa : conversasPersistidas) {
            persistidasPorTelefone.putIfAbsent(conversa.getTelefone(), conversa);
        }

        Map<String, WhatsappConversaResponse> resultado = new LinkedHashMap<>();

        for (WhatsappConversaOperacionalGatewayItemDTO operacional : operacionais.values()) {
            if (!deveExibirConversaOperacional(operacional)) {
                continue;
            }

            WhatsappConversa persistida = persistidasPorTelefone.get(operacional.telefone());
            resultado.put(
                    operacional.telefone(),
                    montarRespostaMesclada(idOrganizacao, persistida, operacional));
        }

        for (WhatsappConversa conversa : conversasPersistidas) {
            if (resultado.containsKey(conversa.getTelefone())) {
                continue;
            }

            WhatsappConversaOperacionalGatewayItemDTO operacional = operacionais.get(conversa.getTelefone());
            resultado.put(
                    conversa.getTelefone(),
                    montarRespostaMesclada(idOrganizacao, conversa, operacional));
        }

        return resultado.values().stream()
                .sorted(Comparator
                        .comparing((WhatsappConversaResponse item) -> !Boolean.TRUE.equals(item.prontoParaEnvioWhatsapp()))
                        .thenComparing(
                                WhatsappConversaResponse::dtUltimaMensagem,
                                Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    private List<WhatsappConversaResponse> aplicarFiltro(
            List<WhatsappConversaResponse> conversas,
            WhatsappConversaFilter filter) {
        if (filter == null) {
            return conversas;
        }

        String buscaNormalizada = normalizarBusca(filter.busca());

        return conversas.stream()
                .filter(conversa -> correspondeBusca(conversa, buscaNormalizada))
                .filter(conversa -> correspondeProntoWhatsapp(conversa, filter.prontoParaEnvioWhatsapp()))
                .filter(conversa -> correspondeStatus(conversa, filter.status()))
                .filter(conversa -> correspondeNaoLida(conversa, filter.naoLida()))
                .toList();
    }

    private String normalizarBusca(String busca) {
        if (!StringUtils.hasText(busca)) {
            return null;
        }

        return busca.trim().toLowerCase();
    }

    private boolean correspondeBusca(WhatsappConversaResponse conversa, String buscaNormalizada) {
        if (buscaNormalizada == null) {
            return true;
        }

        String nome = conversa.nmContato() != null ? conversa.nmContato().toLowerCase() : "";
        String telefone = conversa.telefone() != null ? conversa.telefone() : "";
        String telefoneDigitos = telefone.replaceAll("\\D", "");
        String buscaDigitos = buscaNormalizada.replaceAll("\\D", "");
        String preview = conversa.ultimaMensagem() != null ? conversa.ultimaMensagem().toLowerCase() : "";

        if (nome.contains(buscaNormalizada)) {
            return true;
        }

        if (telefone.contains(buscaNormalizada)) {
            return true;
        }

        if (!buscaDigitos.isBlank() && telefoneDigitos.contains(buscaDigitos)) {
            return true;
        }

        return preview.contains(buscaNormalizada);
    }

    private boolean correspondeProntoWhatsapp(
            WhatsappConversaResponse conversa,
            Boolean prontoParaEnvioWhatsapp) {
        if (prontoParaEnvioWhatsapp == null) {
            return true;
        }

        return prontoParaEnvioWhatsapp.equals(conversa.prontoParaEnvioWhatsapp());
    }

    private boolean correspondeStatus(WhatsappConversaResponse conversa, WhatsappConversaStatus status) {
        if (status == null) {
            return true;
        }

        return status.equals(conversa.status());
    }

    private boolean correspondeNaoLida(WhatsappConversaResponse conversa, Boolean naoLida) {
        if (naoLida == null) {
            return true;
        }

        return naoLida.equals(conversa.naoLida());
    }

    @Transactional(readOnly = true)
    public WhatsappConversaResponse buscarPorTelefone(Long idOrganizacao, String telefone) {
        String destinatario = normalizarTelefone(telefone);
        WhatsappConversa conversa = conversaRepository
                .findByIdOrganizacaoAndTelefone(idOrganizacao, destinatario)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversa nao encontrada."));
        return toResponse(idOrganizacao, conversa, buscarContato(idOrganizacao, destinatario), null);
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

        WhatsappConversaResponse resposta = toResponse(idOrganizacao, conversa, Optional.of(contato), null);
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

        return toResponse(idOrganizacao, conversa, buscarContato(idOrganizacao, conversa.getTelefone()), null);
    }

    @Transactional
    public void excluir(String telefone) {
        Long idOrganizacao = tenantContextService.idOrganizacaoObrigatoria();
        String destinatario = normalizarTelefone(telefone);
        WhatsappConversa conversa = buscarConversaObrigatoria(idOrganizacao, destinatario);

        WhatsappConversaResponse resposta = toResponse(
                idOrganizacao,
                conversa,
                buscarContato(idOrganizacao, conversa.getTelefone()),
                null);
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

        WhatsappConversaResponse resposta = toResponse(idOrganizacao, conversa, Optional.of(contato), null);
        webSocketService.publicarConversa(idOrganizacao, "MENSAGEM_RECEBIDA", resposta);
        return resposta;
    }

    WhatsappConversaResponse toResponse(
            Long idOrganizacao,
            WhatsappConversa conversa,
            Optional<Contato> contato,
            WhatsappConversaOperacionalGatewayItemDTO operacional) {
        return montarRespostaMesclada(idOrganizacao, conversa, operacional, contato);
    }

    private WhatsappConversaResponse montarRespostaMesclada(
            Long idOrganizacao,
            WhatsappConversa conversa,
            WhatsappConversaOperacionalGatewayItemDTO operacional) {
        String telefone = conversa != null
                ? conversa.getTelefone()
                : operacional != null ? operacional.telefone() : null;
        Optional<Contato> contato = telefone != null
                ? buscarContato(idOrganizacao, telefone)
                : Optional.empty();
        return montarRespostaMesclada(idOrganizacao, conversa, operacional, contato);
    }

    private WhatsappConversaResponse montarRespostaMesclada(
            Long idOrganizacao,
            WhatsappConversa conversa,
            WhatsappConversaOperacionalGatewayItemDTO operacional,
            Optional<Contato> contato) {
        boolean exigeConsentimento = organizacaoConfiguracaoService.exigeConsentimento(idOrganizacao);
        String telefone = operacional != null && StringUtils.hasText(operacional.telefone())
                ? operacional.telefone()
                : conversa != null ? conversa.getTelefone() : null;

        String nome = operacional != null && StringUtils.hasText(operacional.nmContato())
                ? operacional.nmContato()
                : conversa != null ? resolverNome(conversa, contato) : telefone;

        String ultimaMensagem = conversa != null && StringUtils.hasText(conversa.getUltimaMensagem())
                ? conversa.getUltimaMensagem()
                : operacional != null ? operacional.ultimaMensagem() : null;

        String tipoUltimaMensagem = conversa != null && StringUtils.hasText(conversa.getTipoUltimaMensagem())
                ? conversa.getTipoUltimaMensagem()
                : operacional != null ? operacional.tipoUltimaMensagem() : null;

        LocalDateTime dtUltimaMensagem = conversa != null && conversa.getDtUltimaMensagem() != null
                ? conversa.getDtUltimaMensagem()
                : parseRecebidaEm(operacional != null ? operacional.dtUltimaMensagem() : null);

        Boolean naoLida = conversa != null ? conversa.getNaoLida() : Boolean.FALSE;
        WhatsappConversa conversaParaExibicao = conversa != null ? conversa : conversaVirtual(telefone, nome);

        return new WhatsappConversaResponse(
                conversa != null ? conversa.getIdConversa() : null,
                contato.map(Contato::getIdContato).orElse(null),
                resolverTelefoneExibicao(conversaParaExibicao, contato, idOrganizacao),
                nome,
                ultimaMensagem,
                tipoUltimaMensagem,
                resolverStatus(idOrganizacao, contato),
                naoLida,
                dtUltimaMensagem,
                exigeConsentimento,
                operacional != null ? operacional.prontoParaEnvio() : null,
                operacional != null ? operacional.inboundRecebida() : null);
    }

    private WhatsappConversa conversaVirtual(String telefone, String nome) {
        WhatsappConversa conversa = new WhatsappConversa();
        conversa.setTelefone(telefone);
        conversa.setNmContato(nome);
        conversa.setNaoLida(false);
        conversa.setDtUltimaMensagem(LocalDateTime.now());
        return conversa;
    }

    private Map<String, WhatsappConversaOperacionalGatewayItemDTO> carregarOperacionaisGateway(Long idOrganizacao) {
        WhatsappConversasOperacionaisGatewayResposta resposta = gatewayClient.listarConversasOperacionais(idOrganizacao);
        if (!Boolean.TRUE.equals(resposta.sucesso()) || resposta.conversas() == null) {
            return Map.of();
        }

        Map<String, WhatsappConversaOperacionalGatewayItemDTO> mapa = new HashMap<>();
        for (WhatsappConversaOperacionalGatewayItemDTO item : resposta.conversas()) {
            if (item == null || !StringUtils.hasText(item.telefone())) {
                continue;
            }
            mapa.putIfAbsent(item.telefone(), item);
        }
        return mapa;
    }

    private boolean deveExibirConversaOperacional(WhatsappConversaOperacionalGatewayItemDTO operacional) {
        if (operacional == null) {
            return false;
        }

        return Boolean.TRUE.equals(operacional.prontoParaEnvio());
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
