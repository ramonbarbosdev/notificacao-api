package com.notificacao_api.service.whatsapp;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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
import com.notificacao_api.enums.WhatsappConversaAba;
import com.notificacao_api.enums.WhatsappConversaOrigem;
import com.notificacao_api.enums.WhatsappConversaStatus;
import com.notificacao_api.enums.WhatsappMensagemDirecao;
import com.notificacao_api.enums.WhatsappMensagemStatus;
import com.notificacao_api.enums.WhatsappMensagemTipo;
import com.notificacao_api.enums.WhatsappProvedorEnvio;
import com.notificacao_api.model.Contato;
import com.notificacao_api.model.WhatsappConversa;
import com.notificacao_api.model.WhatsappConversaOculta;
import com.notificacao_api.model.WhatsappMensagem;
import com.notificacao_api.repository.ContatoRepository;
import com.notificacao_api.repository.WhatsappConversaOcultaRepository;
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
    private final WhatsappConversaOcultaRepository conversaOcultaRepository;
    private final ContatoRepository contatoRepository;
    private final ContatoService contatoService;
    private final OrganizacaoConfiguracaoService organizacaoConfiguracaoService;
    private final WhatsappConexaoWebSocketService webSocketService;
    private final WhatsAppGatewayClient gatewayClient;

    public WhatsappConversaService(
            TenantContextService tenantContextService,
            WhatsappConversaRepository conversaRepository,
            WhatsappConversaOcultaRepository conversaOcultaRepository,
            ContatoRepository contatoRepository,
            ContatoService contatoService,
            OrganizacaoConfiguracaoService organizacaoConfiguracaoService,
            WhatsappConexaoWebSocketService webSocketService,
            WhatsAppGatewayClient gatewayClient) {
        this.tenantContextService = tenantContextService;
        this.conversaRepository = conversaRepository;
        this.conversaOcultaRepository = conversaOcultaRepository;
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
        Set<String> telefonesOcultos = carregarTelefonesOcultos(idOrganizacao);

        Map<String, WhatsappConversa> persistidasPorTelefone = new LinkedHashMap<>();
        for (WhatsappConversa conversa : conversasPersistidas) {
            persistidasPorTelefone.putIfAbsent(conversa.getTelefone(), conversa);
        }

        Map<String, WhatsappConversaResponse> resultado = new LinkedHashMap<>();

        for (WhatsappConversaOperacionalGatewayItemDTO operacional : operacionais.values()) {
            if (!deveExibirConversaOperacional(operacional)) {
                continue;
            }

            if (telefonesOcultos.contains(operacional.telefone())) {
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

            if (telefonesOcultos.contains(conversa.getTelefone())) {
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
                .filter(conversa -> correspondeUltimaDirecao(conversa, filter.ultimaDirecaoMensagem()))
                .filter(conversa -> correspondeOrigem(conversa, filter.origem()))
                .filter(conversa -> correspondeAba(conversa, filter.aba()))
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

    private boolean correspondeUltimaDirecao(
            WhatsappConversaResponse conversa,
            WhatsappMensagemDirecao ultimaDirecaoMensagem) {
        if (ultimaDirecaoMensagem == null) {
            return true;
        }

        return ultimaDirecaoMensagem.equals(conversa.ultimaDirecaoMensagem());
    }

    private boolean correspondeOrigem(WhatsappConversaResponse conversa, WhatsappConversaOrigem origem) {
        if (origem == null) {
            return true;
        }

        return origem.equals(conversa.origem());
    }

    private boolean correspondeAba(WhatsappConversaResponse conversa, WhatsappConversaAba aba) {
        if (aba == null) {
            return true;
        }

        return switch (aba) {
            case INBOX -> Boolean.TRUE.equals(conversa.registradaNaApi());
            case SESSAO -> Boolean.TRUE.equals(conversa.prontoParaEnvioWhatsapp());
        };
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

        Optional<WhatsappConversa> conversaDb = buscarConversaOpcional(idOrganizacao, destinatario);
        String telefoneCanonico = conversaDb.map(WhatsappConversa::getTelefone).orElse(destinatario);

        WhatsappConversaResponse resposta;
        if (conversaDb.isPresent()) {
            WhatsappConversa conversa = conversaDb.get();
            telefoneCanonico = conversa.getTelefone();
            resposta = toResponse(
                    idOrganizacao,
                    conversa,
                    buscarContato(idOrganizacao, conversa.getTelefone()),
                    buscarOperacionalPorTelefone(idOrganizacao, telefoneCanonico));
            conversaRepository.delete(conversa);
        } else {
            WhatsappConversaOperacionalGatewayItemDTO operacional =
                    buscarOperacionalPorTelefone(idOrganizacao, destinatario);
            if (operacional == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversa nao encontrada.");
            }

            telefoneCanonico = operacional.telefone();
            resposta = montarRespostaMesclada(idOrganizacao, null, operacional);
        }

        registrarOculta(idOrganizacao, telefoneCanonico);
        webSocketService.publicarConversa(idOrganizacao, "CONVERSA_EXCLUIDA", resposta);
    }

    @Transactional
    public WhatsappConversaResponse sincronizarInboxDaSessao(String telefoneParam) {
        Long idOrganizacao = tenantContextService.idOrganizacaoObrigatoria();
        String telefone = normalizarTelefone(telefoneParam);

        if (!TelefoneBrasilUtil.celularBrasilComNonoDigito(telefone)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Telefone invalido.");
        }

        Optional<WhatsappConversa> existente = buscarConversaOpcional(idOrganizacao, telefone);
        if (existente.isPresent()) {
            WhatsappConversa conversa = existente.get();
            return toResponse(
                    idOrganizacao,
                    conversa,
                    buscarContato(idOrganizacao, conversa.getTelefone()),
                    buscarOperacionalPorTelefone(idOrganizacao, conversa.getTelefone()));
        }

        WhatsappConversaOperacionalGatewayItemDTO operacional =
                buscarOperacionalPorTelefone(idOrganizacao, telefone);
        if (operacional == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Contato nao encontrado na sessao WhatsApp conectada.");
        }

        boolean temDadosInbound = Boolean.TRUE.equals(operacional.inboundRecebida())
                || StringUtils.hasText(operacional.ultimaMensagem());
        if (!temDadosInbound) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Nao ha mensagem de texto ou midia na sessao para importar. "
                            + "Ligacoes nao geram historico na plataforma. "
                            + "Peca ao contato enviar uma mensagem pelo WhatsApp.");
        }

        WhatsappInboundRequest request = new WhatsappInboundRequest(
                idOrganizacao,
                telefone,
                operacional.jid(),
                null,
                StringUtils.hasText(operacional.tipoUltimaMensagem()) ? operacional.tipoUltimaMensagem() : "texto",
                operacional.ultimaMensagem(),
                operacional.nmContato(),
                operacional.dtUltimaMensagem());

        return registrarInbound(request);
    }

    @Transactional
    public WhatsappConversaResponse registrarInbound(WhatsappInboundRequest request) {
        Long idOrganizacao = request.idOrganizacao();
        String telefone = normalizarTelefone(request.telefone());

        if (!TelefoneBrasilUtil.celularBrasilComNonoDigito(telefone)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Telefone inbound invalido.");
        }

        String preview = request.preview() != null ? request.preview().trim() : null;
        String nome = TelefoneBrasilUtil.resolverNomeContatoWhatsapp(request.nmContato(), telefone);

        corrigirConversaPorJid(idOrganizacao, request.jid(), telefone);

        removerOcultaSeExistir(idOrganizacao, telefone);

        Contato contato = contatoService.registrarInboundPendente(idOrganizacao, telefone, nome);

        WhatsappConversa conversa = conversaRepository
                .findByIdOrganizacaoAndTelefone(idOrganizacao, telefone)
                .orElseGet(() -> {
                    WhatsappConversa nova = new WhatsappConversa();
                    nova.setIdOrganizacao(idOrganizacao);
                    nova.setTelefone(telefone);
                    return nova;
                });

        if (nome != null) {
            conversa.setNmContato(nome);
        }
        conversa.setUltimaMensagem(preview);
        conversa.setTipoUltimaMensagem(request.tipo());
        conversa.setUltimaDirecaoMensagem(WhatsappMensagemDirecao.INBOUND);
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

    @Transactional
    public void registrarOutbound(Long idOrganizacao, String telefone, String mensagem) {
        String destinatario = normalizarTelefone(telefone);
        if (!TelefoneBrasilUtil.celularBrasilComNonoDigito(destinatario)) {
            return;
        }

        String preview = mensagem != null ? mensagem.trim() : null;
        if (preview != null && preview.length() > 160) {
            preview = preview.substring(0, 160);
        }

        Optional<Contato> contato = buscarContato(idOrganizacao, destinatario);
        WhatsappConversa conversa = conversaRepository
                .findByIdOrganizacaoAndTelefone(idOrganizacao, destinatario)
                .orElseGet(() -> {
                    WhatsappConversa nova = new WhatsappConversa();
                    nova.setIdOrganizacao(idOrganizacao);
                    nova.setTelefone(destinatario);
                    contato.ifPresent(item -> {
                        String nome = TelefoneBrasilUtil.resolverNomeContatoWhatsapp(
                                item.getNmContato(),
                                destinatario);
                        if (nome != null) {
                            nova.setNmContato(nome);
                        }
                    });
                    return nova;
                });

        conversa.setUltimaMensagem(preview);
        conversa.setTipoUltimaMensagem("texto");
        conversa.setUltimaDirecaoMensagem(WhatsappMensagemDirecao.OUTBOUND);
        conversa.setNaoLida(false);
        conversa.setDtUltimaMensagem(LocalDateTime.now());

        conversa = conversaRepository.save(conversa);

        WhatsappConversaResponse resposta = toResponse(idOrganizacao, conversa, contato, null);
        webSocketService.publicarConversa(idOrganizacao, "CONVERSA_ATUALIZADA", resposta);
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

        WhatsappMensagemDirecao ultimaDirecaoMensagem = resolverUltimaDirecao(conversa, operacional);
        boolean registradaNaApi = conversa != null && conversa.getIdConversa() != null;
        boolean visivelNaSessaoGateway = operacional != null && visivelNaSessaoGateway(operacional);
        WhatsappConversaOrigem origem = resolverOrigem(registradaNaApi, visivelNaSessaoGateway);

        Boolean naoLida = conversa != null ? conversa.getNaoLida() : Boolean.FALSE;
        WhatsappConversa conversaParaExibicao = conversa != null ? conversa : conversaVirtual(telefone, nome);

        return new WhatsappConversaResponse(
                conversa != null ? conversa.getIdConversa() : null,
                contato.map(Contato::getIdContato).orElse(null),
                resolverTelefoneExibicao(conversaParaExibicao, contato, idOrganizacao),
                nome,
                ultimaMensagem,
                tipoUltimaMensagem,
                ultimaDirecaoMensagem,
                origem,
                registradaNaApi,
                visivelNaSessaoGateway,
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

    private WhatsappMensagemDirecao resolverUltimaDirecao(
            WhatsappConversa conversa,
            WhatsappConversaOperacionalGatewayItemDTO operacional) {
        if (conversa != null && conversa.getUltimaDirecaoMensagem() != null) {
            return conversa.getUltimaDirecaoMensagem();
        }

        if (operacional != null && Boolean.TRUE.equals(operacional.inboundRecebida())) {
            return WhatsappMensagemDirecao.INBOUND;
        }

        return null;
    }

    private boolean visivelNaSessaoGateway(WhatsappConversaOperacionalGatewayItemDTO operacional) {
        return Boolean.TRUE.equals(operacional.prontoParaEnvio())
                || Boolean.TRUE.equals(operacional.inboundRecebida());
    }

    private WhatsappConversaOrigem resolverOrigem(boolean registradaNaApi, boolean visivelNaSessaoGateway) {
        if (registradaNaApi && visivelNaSessaoGateway) {
            return WhatsappConversaOrigem.SINCRONIZADA;
        }

        if (registradaNaApi) {
            return WhatsappConversaOrigem.INBOX;
        }

        if (visivelNaSessaoGateway) {
            return WhatsappConversaOrigem.SESSAO;
        }

        return WhatsappConversaOrigem.INBOX;
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
        return buscarConversaOpcional(idOrganizacao, telefone)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversa nao encontrada."));
    }

    private Optional<WhatsappConversa> buscarConversaOpcional(Long idOrganizacao, String telefone) {
        return conversaRepository
                .findByIdOrganizacaoAndTelefone(idOrganizacao, telefone)
                .or(() -> conversaRepository.findByIdOrganizacaoOrderByDtUltimaMensagemDesc(idOrganizacao).stream()
                        .filter(conversa -> telefone.equals(resolverTelefoneExibicao(
                                conversa,
                                buscarContato(idOrganizacao, conversa.getTelefone()),
                                idOrganizacao)))
                        .findFirst());
    }

    private Set<String> carregarTelefonesOcultos(Long idOrganizacao) {
        Set<String> telefones = conversaOcultaRepository.findTelefonesByIdOrganizacao(idOrganizacao);
        if (telefones == null || telefones.isEmpty()) {
            return Set.of();
        }

        return new HashSet<>(telefones);
    }

    private void registrarOculta(Long idOrganizacao, String telefone) {
        if (!StringUtils.hasText(telefone)) {
            return;
        }

        if (conversaOcultaRepository.existsByIdOrganizacaoAndTelefone(idOrganizacao, telefone)) {
            return;
        }

        WhatsappConversaOculta oculta = new WhatsappConversaOculta();
        oculta.setIdOrganizacao(idOrganizacao);
        oculta.setTelefone(telefone);
        conversaOcultaRepository.save(oculta);
    }

    private void removerOcultaSeExistir(Long idOrganizacao, String telefone) {
        if (!StringUtils.hasText(telefone)) {
            return;
        }

        conversaOcultaRepository.deleteByIdOrganizacaoAndTelefone(idOrganizacao, telefone);
    }

    private WhatsappConversaOperacionalGatewayItemDTO buscarOperacionalPorTelefone(
            Long idOrganizacao,
            String telefone) {
        Map<String, WhatsappConversaOperacionalGatewayItemDTO> operacionais =
                carregarOperacionaisGateway(idOrganizacao);

        WhatsappConversaOperacionalGatewayItemDTO direto = operacionais.get(telefone);
        if (direto != null) {
            return direto;
        }

        String telefoneNormalizado = normalizarTelefone(telefone);
        for (WhatsappConversaOperacionalGatewayItemDTO item : operacionais.values()) {
            if (item == null || !StringUtils.hasText(item.telefone())) {
                continue;
            }

            if (telefoneNormalizado.equals(normalizarTelefone(item.telefone()))) {
                return item;
            }
        }

        return null;
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
        if (contato.isPresent()) {
            String nomeContato = TelefoneBrasilUtil.resolverNomeContatoWhatsapp(
                    contato.get().getNmContato(),
                    conversa.getTelefone());
            if (nomeContato != null) {
                return nomeContato;
            }
        }
        if (StringUtils.hasText(conversa.getNmContato())
                && !TelefoneBrasilUtil.nomePareceTelefone(conversa.getNmContato(), conversa.getTelefone())) {
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
