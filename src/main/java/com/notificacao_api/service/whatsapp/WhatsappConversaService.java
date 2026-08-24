package com.notificacao_api.service.whatsapp;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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
import com.notificacao_api.dto.whatsapp.WhatsappMensagemResponse;
import com.notificacao_api.dto.whatsapp.WhatsappMensagemSessaoItemDTO;
import com.notificacao_api.dto.whatsapp.WhatsappMensagensGatewayResposta;
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
    private final WhatsappMensagemRepository mensagemRepository;
    private final WhatsappInboundService inboundService;

    public WhatsappConversaService(
            TenantContextService tenantContextService,
            WhatsappConversaRepository conversaRepository,
            WhatsappConversaOcultaRepository conversaOcultaRepository,
            ContatoRepository contatoRepository,
            ContatoService contatoService,
            OrganizacaoConfiguracaoService organizacaoConfiguracaoService,
            WhatsappConexaoWebSocketService webSocketService,
            WhatsAppGatewayClient gatewayClient,
            WhatsappMensagemRepository mensagemRepository,
            @Lazy WhatsappInboundService inboundService) {
        this.tenantContextService = tenantContextService;
        this.conversaRepository = conversaRepository;
        this.conversaOcultaRepository = conversaOcultaRepository;
        this.contatoRepository = contatoRepository;
        this.contatoService = contatoService;
        this.organizacaoConfiguracaoService = organizacaoConfiguracaoService;
        this.webSocketService = webSocketService;
        this.gatewayClient = gatewayClient;
        this.mensagemRepository = mensagemRepository;
        this.inboundService = inboundService;
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
        reconciliarDuplicatasPorTelefoneCanonico(idOrganizacao);

        List<WhatsappConversa> conversasPersistidas =
                conversaRepository.findByIdOrganizacaoOrderByDtUltimaMensagemDesc(idOrganizacao);
        Map<String, WhatsappConversaOperacionalGatewayItemDTO> operacionais =
                carregarOperacionaisGatewayPorCanonico(idOrganizacao);
        Set<String> telefonesOcultos = carregarTelefonesOcultosCanonico(idOrganizacao);

        Map<String, WhatsappConversa> persistidasPorCanonico = indexarPersistidasPorCanonico(conversasPersistidas);

        Map<String, WhatsappConversaResponse> resultado = new LinkedHashMap<>();
        Set<String> chaves = new LinkedHashSet<>();
        chaves.addAll(operacionais.keySet());
        chaves.addAll(persistidasPorCanonico.keySet());

        for (String chave : chaves) {
            if (!StringUtils.hasText(chave) || telefonesOcultos.contains(chave)) {
                continue;
            }

            WhatsappConversa persistida = persistidasPorCanonico.get(chave);
            WhatsappConversaOperacionalGatewayItemDTO operacional = operacionais.get(chave);

            if (operacional != null && !deveExibirConversaOperacional(operacional) && persistida == null) {
                continue;
            }

            resultado.put(
                    chave,
                    montarRespostaMesclada(idOrganizacao, persistida, operacional));
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
        WhatsappConversa conversa = buscarConversaOpcional(idOrganizacao, destinatario)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversa nao encontrada."));
        return toResponse(
                idOrganizacao,
                conversa,
                buscarContato(idOrganizacao, conversa.getTelefone()),
                buscarOperacionalPorTelefone(idOrganizacao, conversa.getTelefone()));
    }

    @Transactional(readOnly = true)
    public Page<WhatsappMensagemResponse> listarMensagens(String telefoneParam, Pageable pageable) {
        Long idOrganizacao = tenantContextService.idOrganizacaoObrigatoria();
        String telefone = normalizarTelefone(telefoneParam);
        Set<String> variantes = coletarVariantesTelefone(idOrganizacao, telefone);

        long total = mensagemRepository.countByIdOrganizacaoAndTelefoneIn(idOrganizacao, variantes);
        List<WhatsappMensagem> mensagens = mensagemRepository.findByIdOrganizacaoAndTelefoneInOrderByDtCriacaoAsc(
                idOrganizacao,
                variantes,
                pageable);

        List<WhatsappMensagemResponse> conteudo = mensagens.stream()
                .map(this::toMensagemResponse)
                .toList();

        return new PageImpl<>(conteudo, pageable, total);
    }

    @Transactional
    public WhatsappConversaResponse sincronizarHistoricoDaSessao(String telefoneParam) {
        Long idOrganizacao = tenantContextService.idOrganizacaoObrigatoria();
        String telefone = normalizarTelefone(telefoneParam);

        if (!TelefoneBrasilUtil.celularBrasilComNonoDigito(telefone)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Telefone invalido.");
        }

        WhatsappMensagensGatewayResposta respostaGateway = gatewayClient.listarMensagensSessao(idOrganizacao, telefone);
        if (Boolean.TRUE.equals(respostaGateway.sucesso()) && respostaGateway.mensagens() != null) {
            List<WhatsappInboundRequest> lote = respostaGateway.mensagens().stream()
                    .filter(item -> item != null)
                    .map(item -> new WhatsappInboundRequest(
                            idOrganizacao,
                            telefone,
                            null,
                            item.idMensagemExterna(),
                            item.tipo(),
                            item.preview(),
                            null,
                            item.enviadaEm(),
                            item.direcao()))
                    .toList();
            inboundService.processarLote(lote);
        }

        marcarComoLida(telefone);
        return buscarPorTelefone(idOrganizacao, telefone);
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

        WhatsappConversa conversa = buscarConversaOpcional(idOrganizacao, telefone)
                .orElseGet(() -> {
                    WhatsappConversa nova = new WhatsappConversa();
                    nova.setIdOrganizacao(idOrganizacao);
                    nova.setTelefone(telefone);
                    return nova;
                });

        String nomeConversa = resolverNomeParaConversa(contato, nome, telefone);
        if (nomeConversa != null) {
            conversa.setNmContato(nomeConversa);
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

        salvarMensagemOutbound(idOrganizacao, destinatario, preview, null, null);

        Optional<Contato> contato = buscarContato(idOrganizacao, destinatario);
        WhatsappConversa conversa = buscarConversaOpcional(idOrganizacao, destinatario)
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

        conversa.setTelefone(destinatario);
        conversa.setUltimaMensagem(preview);
        conversa.setTipoUltimaMensagem("texto");
        conversa.setUltimaDirecaoMensagem(WhatsappMensagemDirecao.OUTBOUND);
        conversa.setNaoLida(false);
        conversa.setDtUltimaMensagem(LocalDateTime.now());

        conversa = conversaRepository.save(conversa);

        WhatsappConversaResponse resposta = toResponse(
                idOrganizacao,
                conversa,
                contato,
                buscarOperacionalPorTelefone(idOrganizacao, destinatario));
        webSocketService.publicarConversa(idOrganizacao, "CONVERSA_ATUALIZADA", resposta);
    }

    @Transactional
    public void registrarOutboundSessao(WhatsappInboundRequest request) {
        Long idOrganizacao = request.idOrganizacao();
        String telefone = normalizarTelefone(request.telefone());
        if (!TelefoneBrasilUtil.celularBrasilComNonoDigito(telefone)) {
            return;
        }

        String preview = request.preview() != null ? request.preview().trim() : null;
        if (preview != null && preview.length() > 160) {
            preview = preview.substring(0, 160);
        }

        LocalDateTime dataMensagem = parseRecebidaEm(request.recebidaEm());
        Optional<Contato> contato = buscarContato(idOrganizacao, telefone);
        WhatsappConversa conversa = buscarConversaOpcional(idOrganizacao, telefone)
                .orElseGet(() -> {
                    WhatsappConversa nova = new WhatsappConversa();
                    nova.setIdOrganizacao(idOrganizacao);
                    nova.setTelefone(telefone);
                    return nova;
                });

        if (conversa.getDtUltimaMensagem() == null || !dataMensagem.isBefore(conversa.getDtUltimaMensagem())) {
            conversa.setTelefone(telefone);
            conversa.setUltimaMensagem(preview);
            conversa.setTipoUltimaMensagem(request.tipo());
            conversa.setUltimaDirecaoMensagem(WhatsappMensagemDirecao.OUTBOUND);
            conversa.setNaoLida(false);
            conversa.setDtUltimaMensagem(dataMensagem);
            conversaRepository.save(conversa);

            WhatsappConversaResponse resposta = toResponse(
                    idOrganizacao,
                    conversa,
                    contato,
                    buscarOperacionalPorTelefone(idOrganizacao, telefone));
            webSocketService.publicarConversa(idOrganizacao, "CONVERSA_ATUALIZADA", resposta);
        }
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
        String telefoneCanonico = resolverTelefoneCanonico(conversa, operacional);

        String nome = resolverNomeMesclado(conversa, operacional, contato, telefoneCanonico);

        PreviewMesclado preview = resolverPreviewMesclado(conversa, operacional);
        WhatsappMensagemDirecao ultimaDirecaoMensagem = preview.direcao() != null
                ? preview.direcao()
                : resolverUltimaDirecao(conversa, operacional);
        boolean registradaNaApi = conversa != null && conversa.getIdConversa() != null;
        boolean visivelNaSessaoGateway = operacional != null && visivelNaSessaoGateway(operacional);
        WhatsappConversaOrigem origem = resolverOrigem(registradaNaApi, visivelNaSessaoGateway);

        Boolean naoLida = Boolean.TRUE.equals(conversa != null ? conversa.getNaoLida() : Boolean.FALSE);
        WhatsappConversa conversaParaExibicao = conversa != null ? conversa : conversaVirtual(telefoneCanonico, nome);
        if (conversa != null) {
            conversaParaExibicao.setTelefone(telefoneCanonico);
        }

        return new WhatsappConversaResponse(
                conversa != null ? conversa.getIdConversa() : null,
                contato.map(Contato::getIdContato).orElse(null),
                resolverTelefoneExibicao(conversaParaExibicao, contato, idOrganizacao),
                nome,
                preview.conteudo(),
                preview.tipo(),
                ultimaDirecaoMensagem,
                origem,
                registradaNaApi,
                visivelNaSessaoGateway,
                resolverStatus(idOrganizacao, contato),
                naoLida,
                preview.data(),
                exigeConsentimento,
                operacional != null ? operacional.prontoParaEnvio() : null,
                operacional != null ? operacional.inboundRecebida() : null);
    }

    private record PreviewMesclado(
            String conteudo,
            String tipo,
            LocalDateTime data,
            WhatsappMensagemDirecao direcao) {
    }

    private PreviewMesclado resolverPreviewMesclado(
            WhatsappConversa conversa,
            WhatsappConversaOperacionalGatewayItemDTO operacional) {
        LocalDateTime dtConversa = conversa != null ? conversa.getDtUltimaMensagem() : null;
        LocalDateTime dtOperacional = parseRecebidaEm(operacional != null ? operacional.dtUltimaMensagem() : null);

        boolean operacionalMaisRecente = operacional != null
                && StringUtils.hasText(operacional.ultimaMensagem())
                && (dtConversa == null || (dtOperacional != null && dtOperacional.isAfter(dtConversa)));

        if (operacionalMaisRecente) {
            WhatsappMensagemDirecao direcao = Boolean.TRUE.equals(operacional.inboundRecebida())
                    ? WhatsappMensagemDirecao.INBOUND
                    : null;
            return new PreviewMesclado(
                    operacional.ultimaMensagem(),
                    operacional.tipoUltimaMensagem(),
                    dtOperacional,
                    direcao);
        }

        if (conversa != null && StringUtils.hasText(conversa.getUltimaMensagem())) {
            return new PreviewMesclado(
                    conversa.getUltimaMensagem(),
                    conversa.getTipoUltimaMensagem(),
                    dtConversa,
                    conversa.getUltimaDirecaoMensagem());
        }

        if (operacional != null && StringUtils.hasText(operacional.ultimaMensagem())) {
            return new PreviewMesclado(
                    operacional.ultimaMensagem(),
                    operacional.tipoUltimaMensagem(),
                    dtOperacional,
                    Boolean.TRUE.equals(operacional.inboundRecebida())
                            ? WhatsappMensagemDirecao.INBOUND
                            : null);
        }

        return new PreviewMesclado(null, null, dtConversa != null ? dtConversa : dtOperacional, null);
    }

    private String resolverNomeMesclado(
            WhatsappConversa conversa,
            WhatsappConversaOperacionalGatewayItemDTO operacional,
            Optional<Contato> contato,
            String telefoneCanonico) {
        if (contato.isPresent()) {
            String nomeContato = TelefoneBrasilUtil.resolverNomeContatoWhatsapp(
                    contato.get().getNmContato(),
                    telefoneCanonico);
            if (nomeContato != null) {
                return nomeContato;
            }
        }

        if (conversa != null) {
            String nomeConversa = TelefoneBrasilUtil.resolverNomeContatoWhatsapp(
                    conversa.getNmContato(),
                    telefoneCanonico);
            if (nomeConversa != null) {
                return nomeConversa;
            }
        }

        if (operacional != null && StringUtils.hasText(operacional.nmContato())
                && !TelefoneBrasilUtil.nomePareceTelefone(operacional.nmContato(), telefoneCanonico)) {
            return operacional.nmContato();
        }

        return telefoneCanonico;
    }

    private String resolverTelefoneCanonico(
            WhatsappConversa conversa,
            WhatsappConversaOperacionalGatewayItemDTO operacional) {
        if (operacional != null && StringUtils.hasText(operacional.telefone())) {
            return normalizarTelefone(operacional.telefone());
        }

        if (conversa != null && StringUtils.hasText(conversa.getTelefone())) {
            return normalizarTelefone(conversa.getTelefone());
        }

        return null;
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
        String telefoneCanonico = normalizarTelefone(telefone);
        Optional<Contato> direto = contatoRepository.findByOrganizacao_IdOrganizacaoAndCanalAndDestinatario(
                idOrganizacao,
                CanalNotificacao.WHATSAPP,
                telefoneCanonico);
        if (direto.isPresent()) {
            return direto;
        }

        for (String variante : coletarVariantesTelefone(idOrganizacao, telefoneCanonico)) {
            Optional<Contato> encontrado = contatoRepository.findByOrganizacao_IdOrganizacaoAndCanalAndDestinatario(
                    idOrganizacao,
                    CanalNotificacao.WHATSAPP,
                    variante);
            if (encontrado.isPresent()) {
                return encontrado;
            }
        }

        return Optional.empty();
    }

    private String resolverNomeParaConversa(Contato contato, String nomeInbound, String telefone) {
        String nomeContato = TelefoneBrasilUtil.resolverNomeContatoWhatsapp(contato.getNmContato(), telefone);
        if (nomeContato != null) {
            return nomeContato;
        }

        return nomeInbound;
    }

    private WhatsappConversa buscarConversaObrigatoria(Long idOrganizacao, String telefone) {
        return buscarConversaOpcional(idOrganizacao, telefone)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversa nao encontrada."));
    }

    private Optional<WhatsappConversa> buscarConversaOpcional(Long idOrganizacao, String telefone) {
        String telefoneCanonico = normalizarTelefone(telefone);

        Optional<WhatsappConversa> direta = conversaRepository.findByIdOrganizacaoAndTelefone(
                idOrganizacao,
                telefoneCanonico);
        if (direta.isPresent()) {
            return direta;
        }

        return conversaRepository.findByIdOrganizacaoOrderByDtUltimaMensagemDesc(idOrganizacao).stream()
                .filter(conversa -> telefoneCanonico.equals(normalizarTelefone(conversa.getTelefone())))
                .findFirst();
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
        String telefoneNormalizado = TelefoneBrasilUtil.normalizarCelularWhatsapp(conversa.getTelefone());
        if (TelefoneBrasilUtil.celularBrasilComNonoDigito(telefoneNormalizado)) {
            return telefoneNormalizado;
        }

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
            String canonico = normalizarTelefone(conversa.getTelefone());
            if (!canonico.equals(conversa.getTelefone()) && TelefoneBrasilUtil.celularBrasilComNonoDigito(canonico)) {
                aplicarCorrecaoTelefone(idOrganizacao, conversa, canonico);
                continue;
            }

            if (TelefoneBrasilUtil.celularBrasilComNonoDigito(conversa.getTelefone())) {
                continue;
            }

            String telefoneCorreto = buscarTelefoneCorretoPorNome(idOrganizacao, conversa.getNmContato());
            if (telefoneCorreto != null) {
                aplicarCorrecaoTelefone(idOrganizacao, conversa, telefoneCorreto);
            }
        }
    }

    @Transactional
    void reconciliarDuplicatasPorTelefoneCanonico(Long idOrganizacao) {
        List<WhatsappConversa> conversas = conversaRepository.findByIdOrganizacaoOrderByDtUltimaMensagemDesc(
                idOrganizacao);
        Map<String, List<WhatsappConversa>> porCanonico = new LinkedHashMap<>();

        for (WhatsappConversa conversa : conversas) {
            String chave = normalizarTelefone(conversa.getTelefone());
            if (!StringUtils.hasText(chave)) {
                continue;
            }
            porCanonico.computeIfAbsent(chave, ignored -> new ArrayList<>()).add(conversa);
        }

        for (Map.Entry<String, List<WhatsappConversa>> entry : porCanonico.entrySet()) {
            if (entry.getValue().size() < 2) {
                continue;
            }

            WhatsappConversa keeper = entry.getValue().get(0);
            for (int i = 1; i < entry.getValue().size(); i++) {
                aplicarCorrecaoTelefone(idOrganizacao, entry.getValue().get(i), keeper.getTelefone());
            }

            if (!entry.getKey().equals(keeper.getTelefone())) {
                aplicarCorrecaoTelefone(idOrganizacao, keeper, entry.getKey());
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
            if (conversa.getDtUltimaMensagem() != null
                    && (keeper.getDtUltimaMensagem() == null
                            || conversa.getDtUltimaMensagem().isAfter(keeper.getDtUltimaMensagem()))) {
                keeper.setUltimaMensagem(conversa.getUltimaMensagem());
                keeper.setTipoUltimaMensagem(conversa.getTipoUltimaMensagem());
                keeper.setUltimaDirecaoMensagem(conversa.getUltimaDirecaoMensagem());
                keeper.setDtUltimaMensagem(conversa.getDtUltimaMensagem());
                keeper.setNaoLida(Boolean.TRUE.equals(keeper.getNaoLida()) || Boolean.TRUE.equals(conversa.getNaoLida()));
                if (StringUtils.hasText(conversa.getNmContato())) {
                    keeper.setNmContato(conversa.getNmContato());
                }
                if (StringUtils.hasText(conversa.getJid())) {
                    keeper.setJid(conversa.getJid());
                }
            }
            mensagemRepository.atualizarTelefone(idOrganizacao, telefoneErrado, telefoneCorreto);
            conversaRepository.delete(conversa);
            conversaRepository.save(keeper);
            return;
        }

        conversa.setTelefone(telefoneCorreto);
        conversaRepository.save(conversa);
        mensagemRepository.atualizarTelefone(idOrganizacao, telefoneErrado, telefoneCorreto);

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

    public LocalDateTime parseRecebidaEmPublico(String recebidaEm) {
        return parseRecebidaEm(recebidaEm);
    }

    private Map<String, WhatsappConversa> indexarPersistidasPorCanonico(List<WhatsappConversa> conversas) {
        Map<String, WhatsappConversa> mapa = new LinkedHashMap<>();
        for (WhatsappConversa conversa : conversas) {
            String chave = normalizarTelefone(conversa.getTelefone());
            mapa.merge(chave, conversa, this::escolherConversaMaisRecente);
        }
        return mapa;
    }

    private WhatsappConversa escolherConversaMaisRecente(WhatsappConversa atual, WhatsappConversa candidata) {
        if (atual.getDtUltimaMensagem() == null) {
            return candidata;
        }
        if (candidata.getDtUltimaMensagem() == null) {
            return atual;
        }
        return candidata.getDtUltimaMensagem().isAfter(atual.getDtUltimaMensagem()) ? candidata : atual;
    }

    private Map<String, WhatsappConversaOperacionalGatewayItemDTO> carregarOperacionaisGatewayPorCanonico(
            Long idOrganizacao) {
        Map<String, WhatsappConversaOperacionalGatewayItemDTO> mapa = new LinkedHashMap<>();
        for (WhatsappConversaOperacionalGatewayItemDTO item : carregarOperacionaisGateway(idOrganizacao).values()) {
            if (item == null || !StringUtils.hasText(item.telefone())) {
                continue;
            }
            String chave = normalizarTelefone(item.telefone());
            mapa.merge(chave, item, this::fundirOperacional);
        }
        return mapa;
    }

    private WhatsappConversaOperacionalGatewayItemDTO fundirOperacional(
            WhatsappConversaOperacionalGatewayItemDTO atual,
            WhatsappConversaOperacionalGatewayItemDTO candidato) {
        boolean pronto = Boolean.TRUE.equals(atual.prontoParaEnvio()) || Boolean.TRUE.equals(candidato.prontoParaEnvio());
        boolean inbound = Boolean.TRUE.equals(atual.inboundRecebida()) || Boolean.TRUE.equals(candidato.inboundRecebida());

        LocalDateTime dtAtual = parseRecebidaEm(atual.dtUltimaMensagem());
        LocalDateTime dtCandidato = parseRecebidaEm(candidato.dtUltimaMensagem());
        boolean candidatoMaisRecente = dtCandidato != null && (dtAtual == null || dtCandidato.isAfter(dtAtual));
        WhatsappConversaOperacionalGatewayItemDTO base = candidatoMaisRecente ? candidato : atual;
        WhatsappConversaOperacionalGatewayItemDTO outro = candidatoMaisRecente ? atual : candidato;

        String nmContato = StringUtils.hasText(base.nmContato()) && !base.nmContato().equals(base.telefone())
                ? base.nmContato()
                : outro.nmContato();

        return new WhatsappConversaOperacionalGatewayItemDTO(
                normalizarTelefone(base.telefone()),
                nmContato,
                StringUtils.hasText(base.jid()) ? base.jid() : outro.jid(),
                pronto,
                pronto,
                inbound,
                StringUtils.hasText(base.ultimaMensagem()) ? base.ultimaMensagem() : outro.ultimaMensagem(),
                StringUtils.hasText(base.tipoUltimaMensagem()) ? base.tipoUltimaMensagem() : outro.tipoUltimaMensagem(),
                StringUtils.hasText(base.dtUltimaMensagem()) ? base.dtUltimaMensagem() : outro.dtUltimaMensagem());
    }

    private Set<String> carregarTelefonesOcultosCanonico(Long idOrganizacao) {
        Set<String> ocultos = carregarTelefonesOcultos(idOrganizacao);
        Set<String> canonico = new HashSet<>();
        for (String telefone : ocultos) {
            canonico.add(normalizarTelefone(telefone));
        }
        return canonico;
    }

    private Set<String> coletarVariantesTelefone(Long idOrganizacao, String telefoneCanonico) {
        Set<String> variantes = new HashSet<>();
        variantes.add(telefoneCanonico);

        conversaRepository.findByIdOrganizacaoOrderByDtUltimaMensagemDesc(idOrganizacao).stream()
                .filter(conversa -> telefoneCanonico.equals(normalizarTelefone(conversa.getTelefone())))
                .map(WhatsappConversa::getTelefone)
                .forEach(variantes::add);

        mensagemRepository.findDistinctTelefonesByIdOrganizacao(idOrganizacao).stream()
                .filter(telefone -> telefoneCanonico.equals(normalizarTelefone(telefone)))
                .forEach(variantes::add);

        return variantes;
    }

    private void salvarMensagemOutbound(
            Long idOrganizacao,
            String telefone,
            String conteudo,
            String idExterno,
            Long idNotificacao) {
        WhatsappMensagem mensagem = new WhatsappMensagem();
        mensagem.setIdOrganizacao(idOrganizacao);
        mensagem.setIdNotificacao(idNotificacao);
        mensagem.setProvider(WhatsappProvedorEnvio.WHATSAPP_GATEWAY);
        mensagem.setTelefone(telefone);
        mensagem.setDirecao(WhatsappMensagemDirecao.OUTBOUND);
        mensagem.setTipo(WhatsappMensagemTipo.TEXT);
        mensagem.setConteudo(conteudo);
        mensagem.setIdExterno(idExterno);
        mensagem.setStatus(WhatsappMensagemStatus.SENT);
        mensagem.setDtEnvio(LocalDateTime.now());
        mensagemRepository.save(mensagem);
    }

    private WhatsappMensagemResponse toMensagemResponse(WhatsappMensagem mensagem) {
        return new WhatsappMensagemResponse(
                mensagem.getIdMensagem(),
                normalizarTelefone(mensagem.getTelefone()),
                mensagem.getDirecao(),
                mensagem.getTipo(),
                mensagem.getConteudo(),
                mensagem.getStatus(),
                mensagem.getIdExterno(),
                mensagem.getDtEnvio(),
                mensagem.getDtCriacao());
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
