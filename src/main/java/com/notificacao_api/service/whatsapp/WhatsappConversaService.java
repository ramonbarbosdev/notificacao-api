package com.notificacao_api.service.whatsapp;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
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
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import com.notificacao_api.config.PropriedadesProtecaoNotificacao;
import com.notificacao_api.dto.whatsapp.StatusWhatsappResposta;
import com.notificacao_api.dto.whatsapp.WhatsappCarregarMaisMensagensResponse;
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
import com.notificacao_api.enums.WhatsappSessionStatus;
import com.notificacao_api.model.WhatsappConversa;
import com.notificacao_api.model.WhatsappConversaOculta;
import com.notificacao_api.repository.WhatsappConversaOcultaRepository;
import com.notificacao_api.repository.WhatsappConversaRepository;
import com.notificacao_api.repository.WhatsappSessionRepository;
import com.notificacao_api.service.TenantContextService;
import com.notificacao_api.shared.TelefoneBrasilUtil;

@Service
public class WhatsappConversaService {

    private final TenantContextService tenantContextService;
    private final WhatsappConversaRepository conversaRepository;
    private final WhatsappConversaOcultaRepository conversaOcultaRepository;
    private final WhatsappSessionRepository whatsappSessionRepository;
    private final WhatsappConexaoWebSocketService webSocketService;
    private final WhatsAppGatewayClient gatewayClient;
    private final WhatsappMensagemService mensagemService;
    private final ZoneId fusoHorarioAplicacao;
    private final ConcurrentHashMap<Long, ConcurrentHashMap<String, WhatsappConversa>> conversasSessao =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Set<String>> telefonesOcultosSessao = new ConcurrentHashMap<>();

    public WhatsappConversaService(
            TenantContextService tenantContextService,
            WhatsappConversaRepository conversaRepository,
            WhatsappConversaOcultaRepository conversaOcultaRepository,
            WhatsappSessionRepository whatsappSessionRepository,
            WhatsappConexaoWebSocketService webSocketService,
            WhatsAppGatewayClient gatewayClient,
            WhatsappMensagemService mensagemService,
            PropriedadesProtecaoNotificacao propriedadesProtecaoNotificacao) {
        this.tenantContextService = tenantContextService;
        this.conversaRepository = conversaRepository;
        this.conversaOcultaRepository = conversaOcultaRepository;
        this.whatsappSessionRepository = whatsappSessionRepository;
        this.webSocketService = webSocketService;
        this.gatewayClient = gatewayClient;
        this.mensagemService = mensagemService;
        this.fusoHorarioAplicacao = ZoneId.of(propriedadesProtecaoNotificacao.fusoHorario());
    }

    @Transactional
    public void limparDadosSessao(Long idOrganizacao) {
        conversasSessao.remove(idOrganizacao);
        telefonesOcultosSessao.remove(idOrganizacao);
        conversaRepository.deleteByIdOrganizacao(idOrganizacao);
        conversaOcultaRepository.deleteByIdOrganizacao(idOrganizacao);
        mensagemService.limparMensagensGateway(idOrganizacao);

        webSocketService.publicar(
                idOrganizacao,
                "CONVERSAS_LIMPAS",
                WhatsappSessionStatus.NAO_INICIADO.name(),
                true,
                0L,
                "Conversas da sessao WhatsApp foram removidas.");
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
        if (!sessaoDisponivelParaConversas(idOrganizacao)) {
            return List.of();
        }

        Map<String, WhatsappConversaOperacionalGatewayItemDTO> operacionais =
                carregarOperacionaisGatewayPorCanonico(idOrganizacao);
        Map<String, WhatsappConversa> conversasDaSessao =
                conversasSessao.getOrDefault(idOrganizacao, new ConcurrentHashMap<>());
        Set<String> telefonesOcultos = carregarTelefonesOcultosCanonico(idOrganizacao);

        Map<String, WhatsappConversaResponse> resultado = new LinkedHashMap<>();
        Set<String> chaves = new LinkedHashSet<>();
        chaves.addAll(operacionais.keySet());
        chaves.addAll(conversasDaSessao.keySet());
        for (WhatsappConversa conversaPersistida : conversaRepository.findByIdOrganizacaoOrderByDtUltimaMensagemDesc(
                idOrganizacao)) {
            if (conversaPersistida != null && StringUtils.hasText(conversaPersistida.getTelefone())) {
                chaves.add(normalizarTelefone(conversaPersistida.getTelefone()));
            }
        }

        for (String chave : chaves) {
            if (!StringUtils.hasText(chave) || telefonesOcultos.contains(chave)) {
                continue;
            }

            WhatsappConversa conversaSessao = conversasDaSessao.get(chave);
            if (conversaSessao == null) {
                conversaSessao = buscarConversaPorTelefoneExato(idOrganizacao, chave).orElse(null);
            }

            WhatsappConversaOperacionalGatewayItemDTO operacional = operacionais.get(chave);
            boolean temConversaLocal = conversaSessao != null && StringUtils.hasText(conversaSessao.getUltimaMensagem());

            if (operacional != null && !deveExibirConversaOperacional(operacional) && !temConversaLocal) {
                continue;
            }

            if (operacional == null && !temConversaLocal) {
                continue;
            }

            resultado.put(
                    chave,
                    montarRespostaMesclada(idOrganizacao, conversaSessao, operacional));
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
            case INBOX -> correspondeAbaInbox(conversa);
            case SESSAO -> correspondeAbaSessao(conversa);
        };
    }

    private boolean correspondeAbaInbox(WhatsappConversaResponse conversa) {
        if (Boolean.TRUE.equals(conversa.registradaNaApi())) {
            return true;
        }

        return StringUtils.hasText(conversa.ultimaMensagem());
    }

    private boolean correspondeAbaSessao(WhatsappConversaResponse conversa) {
        if (Boolean.TRUE.equals(conversa.prontoParaEnvioWhatsapp())) {
            return true;
        }

        if (Boolean.TRUE.equals(conversa.visivelNaSessaoGateway())) {
            return true;
        }

        return Boolean.TRUE.equals(conversa.inboundRecebidaWhatsapp())
                || StringUtils.hasText(conversa.ultimaMensagem());
    }

    @Transactional(readOnly = true)
    public WhatsappConversaResponse buscarPorTelefone(Long idOrganizacao, String telefone) {
        validarSessaoConectada(idOrganizacao);
        String destinatario = normalizarTelefone(telefone);
        WhatsappConversa conversa = obterConversaSessao(idOrganizacao, destinatario)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversa nao encontrada."));
        return toResponse(
                idOrganizacao,
                conversa,
                buscarOperacionalPorTelefone(idOrganizacao, conversa.getTelefone()));
    }

    @Transactional(readOnly = true)
    public Page<WhatsappMensagemResponse> listarMensagens(
            String telefoneParam,
            WhatsappMensagemDirecao direcao,
            Pageable pageable) {
        Long idOrganizacao = tenantContextService.idOrganizacaoObrigatoria();
        validarSessaoConectada(idOrganizacao);
        String telefone = normalizarTelefone(telefoneParam);

        if (!TelefoneBrasilUtil.celularBrasilComNonoDigito(telefone)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Telefone invalido.");
        }

        int limite = Math.min(Math.max(pageable.getPageSize(), 1), 200);
        WhatsappMensagensGatewayResposta respostaGateway =
                gatewayClient.listarMensagensSessao(idOrganizacao, telefone, limite, direcao);

        if (!Boolean.TRUE.equals(respostaGateway.sucesso()) || respostaGateway.mensagens() == null) {
            return new PageImpl<>(List.of(), pageable, 0);
        }

        List<WhatsappMensagemResponse> mensagens = respostaGateway.mensagens().stream()
                .map(item -> toMensagemResponseGateway(telefone, item))
                .sorted(Comparator
                        .comparing(WhatsappMensagemResponse::dtEnvio, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(WhatsappMensagemResponse::dtCriacao, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        return new PageImpl<>(mensagens, pageable, mensagens.size());
    }

    @Transactional(readOnly = true)
    public WhatsappCarregarMaisMensagensResponse carregarMaisMensagens(
            String telefoneParam,
            Long antesDeIdMensagem,
            String antesDeIdExterno,
            int limite) {
        Long idOrganizacao = tenantContextService.idOrganizacaoObrigatoria();
        validarSessaoConectada(idOrganizacao);
        String telefone = normalizarTelefone(telefoneParam);

        if (!TelefoneBrasilUtil.celularBrasilComNonoDigito(telefone)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Telefone invalido.");
        }

        if (antesDeIdMensagem == null && !StringUtils.hasText(antesDeIdExterno)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe a mensagem de referencia para carregar mais.");
        }

        int limiteNormalizado = Math.min(Math.max(limite, 1), 100);
        var respostaGateway = gatewayClient.carregarMaisHistorico(idOrganizacao, telefone, limiteNormalizado);

        WhatsappMensagensGatewayResposta cacheAtualizado =
                gatewayClient.listarMensagensSessao(idOrganizacao, telefone, 200, null);

        List<WhatsappMensagemResponse> mensagensOrdenadas = List.of();
        if (Boolean.TRUE.equals(cacheAtualizado.sucesso()) && cacheAtualizado.mensagens() != null) {
            mensagensOrdenadas = cacheAtualizado.mensagens().stream()
                    .map(item -> toMensagemResponseGateway(telefone, item))
                    .sorted(Comparator
                            .comparing(WhatsappMensagemResponse::dtEnvio, Comparator.nullsLast(Comparator.naturalOrder()))
                            .thenComparing(WhatsappMensagemResponse::dtCriacao, Comparator.nullsLast(Comparator.naturalOrder())))
                    .toList();
        }

        int indiceReferencia = encontrarIndiceMensagemReferencia(
                mensagensOrdenadas,
                antesDeIdMensagem,
                antesDeIdExterno);

        List<WhatsappMensagemResponse> anteriores = List.of();
        if (indiceReferencia > 0) {
            int inicio = Math.max(0, indiceReferencia - limiteNormalizado);
            anteriores = mensagensOrdenadas.subList(inicio, indiceReferencia);
        }

        boolean fimHistorico = Boolean.TRUE.equals(respostaGateway.fimHistorico())
                || indiceReferencia <= 0
                || anteriores.isEmpty();

        return new WhatsappCarregarMaisMensagensResponse(anteriores, fimHistorico, anteriores.size());
    }

    private int encontrarIndiceMensagemReferencia(
            List<WhatsappMensagemResponse> mensagens,
            Long antesDeIdMensagem,
            String antesDeIdExterno) {
        for (int indice = 0; indice < mensagens.size(); indice++) {
            WhatsappMensagemResponse mensagem = mensagens.get(indice);
            if (antesDeIdMensagem != null && antesDeIdMensagem.equals(mensagem.idMensagem())) {
                return indice;
            }

            if (StringUtils.hasText(antesDeIdExterno)
                    && antesDeIdExterno.equals(mensagem.idExterno())) {
                return indice;
            }
        }

        return -1;
    }

    @Transactional
    public WhatsappConversaResponse sincronizarHistoricoDaSessao(String telefoneParam) {
        Long idOrganizacao = tenantContextService.idOrganizacaoObrigatoria();
        String telefone = normalizarTelefone(telefoneParam);

        if (!TelefoneBrasilUtil.celularBrasilComNonoDigito(telefone)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Telefone invalido.");
        }

        marcarComoLida(telefone);
        return buscarPorTelefone(idOrganizacao, telefone);
    }

    @Transactional
    public WhatsappConversaResponse marcarComoLida(String telefone) {
        Long idOrganizacao = tenantContextService.idOrganizacaoObrigatoria();
        validarSessaoConectada(idOrganizacao);
        String destinatario = normalizarTelefone(telefone);

        WhatsappConversa conversa = obterConversaSessao(idOrganizacao, destinatario)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversa nao encontrada."));

        conversa.setNaoLida(false);
        salvarConversaSessao(idOrganizacao, destinatario, conversa);

        return toResponse(idOrganizacao, conversa, buscarOperacionalPorTelefone(idOrganizacao, conversa.getTelefone()));
    }

    @Transactional
    public void excluir(String telefone) {
        Long idOrganizacao = tenantContextService.idOrganizacaoObrigatoria();
        validarSessaoConectada(idOrganizacao);
        String destinatario = normalizarTelefone(telefone);

        Optional<WhatsappConversa> conversaSessao = obterConversaSessao(idOrganizacao, destinatario);
        String telefoneCanonico = conversaSessao.map(WhatsappConversa::getTelefone).orElse(destinatario);

        WhatsappConversaResponse resposta;
        if (conversaSessao.isPresent()) {
            WhatsappConversa conversa = conversaSessao.get();
            telefoneCanonico = conversa.getTelefone();
            resposta = toResponse(
                    idOrganizacao,
                    conversa,
                    buscarOperacionalPorTelefone(idOrganizacao, telefoneCanonico));
            removerConversaSessao(idOrganizacao, telefoneCanonico);
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
        validarSessaoConectada(idOrganizacao);
        String telefone = normalizarTelefone(telefoneParam);

        if (!TelefoneBrasilUtil.celularBrasilComNonoDigito(telefone)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Telefone invalido.");
        }

        Optional<WhatsappConversa> existente = obterConversaSessao(idOrganizacao, telefone);
        if (existente.isPresent()) {
            WhatsappConversa conversa = existente.get();
            return toResponse(
                    idOrganizacao,
                    conversa,
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
        validarSessaoConectada(idOrganizacao);
        String telefone = normalizarTelefone(request.telefone());

        if (!TelefoneBrasilUtil.celularBrasilComNonoDigito(telefone)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Telefone inbound invalido.");
        }

        removerOcultaSeExistir(idOrganizacao, telefone);

        WhatsappConversa conversa = atualizarConversaSessao(idOrganizacao, request, true);

        WhatsappConversaResponse resposta = toResponse(
                idOrganizacao,
                conversa,
                buscarOperacionalPorTelefone(idOrganizacao, telefone));
        webSocketService.publicarConversa(idOrganizacao, "MENSAGEM_RECEBIDA", resposta);
        return resposta;
    }

    @Transactional
    public void registrarOutbound(Long idOrganizacao, String telefone, String mensagem) {
        if (!sessaoWhatsappConectada(idOrganizacao)) {
            return;
        }

        String destinatario = normalizarTelefone(telefone);
        if (!TelefoneBrasilUtil.celularBrasilComNonoDigito(destinatario)) {
            return;
        }

        String preview = mensagem != null ? mensagem.trim() : null;
        if (preview != null && preview.length() > 160) {
            preview = preview.substring(0, 160);
        }

        WhatsappInboundRequest request = new WhatsappInboundRequest(
                idOrganizacao,
                destinatario,
                null,
                null,
                "texto",
                preview,
                null,
                LocalDateTime.now(fusoHorarioAplicacao).toString(),
                WhatsappMensagemDirecao.OUTBOUND.name());

        WhatsappConversa conversa = atualizarConversaSessao(idOrganizacao, request, false);

        WhatsappConversaResponse resposta = toResponse(
                idOrganizacao,
                conversa,
                buscarOperacionalPorTelefone(idOrganizacao, destinatario));
        webSocketService.publicarConversa(idOrganizacao, "CONVERSA_ATUALIZADA", resposta);
    }

    @Transactional
    public void registrarOutboundSessao(WhatsappInboundRequest request) {
        Long idOrganizacao = request.idOrganizacao();
        if (!sessaoWhatsappConectada(idOrganizacao)) {
            return;
        }

        String telefone = normalizarTelefone(request.telefone());
        if (!TelefoneBrasilUtil.celularBrasilComNonoDigito(telefone)) {
            return;
        }

        WhatsappConversa conversa = atualizarConversaSessao(idOrganizacao, request, false);
        WhatsappConversaResponse resposta = toResponse(
                idOrganizacao,
                conversa,
                buscarOperacionalPorTelefone(idOrganizacao, telefone));
        webSocketService.publicarConversa(idOrganizacao, "CONVERSA_ATUALIZADA", resposta);
    }

    WhatsappConversaResponse toResponse(
            Long idOrganizacao,
            WhatsappConversa conversa,
            WhatsappConversaOperacionalGatewayItemDTO operacional) {
        return montarRespostaMesclada(idOrganizacao, conversa, operacional);
    }

    private WhatsappConversaResponse montarRespostaMesclada(
            Long idOrganizacao,
            WhatsappConversa conversa,
            WhatsappConversaOperacionalGatewayItemDTO operacional) {
        String telefoneCanonico = resolverTelefoneCanonico(conversa, operacional);

        String nome = resolverNomeMesclado(conversa, operacional, telefoneCanonico);

        PreviewMesclado preview = resolverPreviewMesclado(conversa, operacional);
        WhatsappMensagemDirecao ultimaDirecaoMensagem = preview.direcao() != null
                ? preview.direcao()
                : resolverUltimaDirecao(conversa, operacional);
        boolean registradaNaApi = conversa != null && conversa.getIdConversa() != null;
        boolean visivelNaSessaoGateway = (operacional != null && visivelNaSessaoGateway(operacional))
                || (conversa != null && StringUtils.hasText(conversa.getUltimaMensagem()));
        WhatsappConversaOrigem origem = resolverOrigem(registradaNaApi, visivelNaSessaoGateway);

        Boolean naoLida = Boolean.TRUE.equals(conversa != null ? conversa.getNaoLida() : Boolean.FALSE);
        WhatsappConversa conversaParaExibicao = conversa != null ? conversa : conversaVirtual(telefoneCanonico, nome);
        if (conversa != null) {
            conversaParaExibicao.setTelefone(telefoneCanonico);
        }

        return new WhatsappConversaResponse(
                conversa != null ? conversa.getIdConversa() : null,
                resolverTelefoneExibicao(conversaParaExibicao, idOrganizacao),
                nome,
                preview.conteudo(),
                preview.tipo(),
                ultimaDirecaoMensagem,
                origem,
                registradaNaApi,
                visivelNaSessaoGateway,
                resolverStatus(operacional),
                naoLida,
                preview.data(),
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
            return new PreviewMesclado(
                    operacional.ultimaMensagem(),
                    operacional.tipoUltimaMensagem(),
                    dtOperacional,
                    resolverDirecaoOperacional(operacional));
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
                    resolverDirecaoOperacional(operacional));
        }

        return new PreviewMesclado(null, null, dtConversa != null ? dtConversa : dtOperacional, null);
    }

    private String resolverNomeMesclado(
            WhatsappConversa conversa,
            WhatsappConversaOperacionalGatewayItemDTO operacional,
            String telefoneCanonico) {
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

        if (operacional != null && StringUtils.hasText(operacional.ultimaDirecao())) {
            return resolverDirecaoGateway(operacional.ultimaDirecao());
        }

        if (operacional != null && Boolean.TRUE.equals(operacional.inboundRecebida())) {
            return WhatsappMensagemDirecao.INBOUND;
        }

        return null;
    }

    private boolean visivelNaSessaoGateway(WhatsappConversaOperacionalGatewayItemDTO operacional) {
        return Boolean.TRUE.equals(operacional.prontoParaEnvio())
                || Boolean.TRUE.equals(operacional.inboundRecebida())
                || StringUtils.hasText(operacional.ultimaMensagem());
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
            mapa.putIfAbsent(normalizarTelefone(item.telefone()), item);
        }
        return mapa;
    }

    private boolean deveExibirConversaOperacional(WhatsappConversaOperacionalGatewayItemDTO operacional) {
        if (operacional == null) {
            return false;
        }

        return Boolean.TRUE.equals(operacional.prontoParaEnvio())
                || Boolean.TRUE.equals(operacional.inboundRecebida())
                || StringUtils.hasText(operacional.ultimaMensagem());
    }

    private WhatsappMensagemDirecao resolverDirecaoOperacional(WhatsappConversaOperacionalGatewayItemDTO operacional) {
        if (operacional == null) {
            return null;
        }

        if (StringUtils.hasText(operacional.ultimaDirecao())) {
            return resolverDirecaoGateway(operacional.ultimaDirecao());
        }

        return Boolean.TRUE.equals(operacional.inboundRecebida())
                ? WhatsappMensagemDirecao.INBOUND
                : null;
    }

    private WhatsappConversa buscarConversaObrigatoria(Long idOrganizacao, String telefone) {
        return buscarConversaOpcional(idOrganizacao, telefone)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversa nao encontrada."));
    }

    private Optional<WhatsappConversa> buscarConversaOpcional(Long idOrganizacao, String telefone) {
        String telefoneCanonico = normalizarTelefone(telefone);

        Optional<WhatsappConversa> direta = buscarConversaPorTelefoneExato(idOrganizacao, telefoneCanonico);
        if (direta.isPresent()) {
            return direta;
        }

        return conversaRepository.findByIdOrganizacaoOrderByDtUltimaMensagemDesc(idOrganizacao).stream()
                .filter(conversa -> telefoneCanonico.equals(normalizarTelefone(conversa.getTelefone())))
                .findFirst();
    }

    private Optional<WhatsappConversa> buscarConversaPorTelefoneExato(Long idOrganizacao, String telefone) {
        List<WhatsappConversa> encontradas = conversaRepository.findAllByIdOrganizacaoAndTelefone(
                idOrganizacao,
                telefone);

        if (encontradas.isEmpty()) {
            return Optional.empty();
        }

        if (encontradas.size() == 1) {
            return Optional.of(encontradas.get(0));
        }

        return Optional.of(mesclarConversasDuplicadas(idOrganizacao, encontradas));
    }

    private WhatsappConversa mesclarConversasDuplicadas(Long idOrganizacao, List<WhatsappConversa> duplicatas) {
        WhatsappConversa keeper = duplicatas.stream()
                .max(Comparator.comparing(
                        WhatsappConversa::getDtUltimaMensagem,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .orElse(duplicatas.get(0));

        for (WhatsappConversa duplicata : duplicatas) {
            if (duplicata.getIdConversa().equals(keeper.getIdConversa())) {
                continue;
            }

            if (duplicata.getDtUltimaMensagem() != null
                    && (keeper.getDtUltimaMensagem() == null
                            || duplicata.getDtUltimaMensagem().isAfter(keeper.getDtUltimaMensagem()))) {
                keeper.setUltimaMensagem(duplicata.getUltimaMensagem());
                keeper.setTipoUltimaMensagem(duplicata.getTipoUltimaMensagem());
                keeper.setUltimaDirecaoMensagem(duplicata.getUltimaDirecaoMensagem());
                keeper.setDtUltimaMensagem(duplicata.getDtUltimaMensagem());
                keeper.setNaoLida(Boolean.TRUE.equals(keeper.getNaoLida()) || Boolean.TRUE.equals(duplicata.getNaoLida()));
            }

            if (!StringUtils.hasText(keeper.getNmContato()) && StringUtils.hasText(duplicata.getNmContato())) {
                keeper.setNmContato(duplicata.getNmContato());
            }

            if (!StringUtils.hasText(keeper.getJid()) && StringUtils.hasText(duplicata.getJid())) {
                keeper.setJid(duplicata.getJid());
            }

            conversaRepository.delete(duplicata);
        }

        return conversaRepository.save(keeper);
    }

    private Set<String> carregarTelefonesOcultos(Long idOrganizacao) {
        Set<String> telefones = telefonesOcultosSessao.get(idOrganizacao);
        if (telefones == null || telefones.isEmpty()) {
            return Set.of();
        }

        return new HashSet<>(telefones);
    }

    private void registrarOculta(Long idOrganizacao, String telefone) {
        if (!StringUtils.hasText(telefone)) {
            return;
        }

        telefonesOcultosSessao
                .computeIfAbsent(idOrganizacao, chave -> ConcurrentHashMap.newKeySet())
                .add(normalizarTelefone(telefone));
    }

    private void removerOcultaSeExistir(Long idOrganizacao, String telefone) {
        if (!StringUtils.hasText(telefone)) {
            return;
        }

        Set<String> ocultos = telefonesOcultosSessao.get(idOrganizacao);
        if (ocultos != null) {
            ocultos.remove(normalizarTelefone(telefone));
        }
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

    private WhatsappConversaStatus resolverStatus(WhatsappConversaOperacionalGatewayItemDTO operacional) {
        if (operacional != null && Boolean.TRUE.equals(operacional.prontoParaEnvio())) {
            return WhatsappConversaStatus.LIBERADO;
        }

        return WhatsappConversaStatus.LIBERADO;
    }

    private String resolverTelefoneExibicao(
            WhatsappConversa conversa,
            Long idOrganizacao) {
        String telefoneNormalizado = TelefoneBrasilUtil.normalizarCelularWhatsapp(conversa.getTelefone());
        if (TelefoneBrasilUtil.celularBrasilComNonoDigito(telefoneNormalizado)) {
            return telefoneNormalizado;
        }

        if (TelefoneBrasilUtil.celularBrasilComNonoDigito(conversa.getTelefone())) {
            return conversa.getTelefone();
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

    private void aplicarCorrecaoTelefone(Long idOrganizacao, WhatsappConversa conversa, String telefoneCorreto) {
        String telefoneErrado = conversa.getTelefone();
        if (telefoneErrado.equals(telefoneCorreto)) {
            return;
        }

        Optional<WhatsappConversa> existente = buscarConversaPorTelefoneExato(idOrganizacao, telefoneCorreto);

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
            conversaRepository.delete(conversa);
            conversaRepository.save(keeper);
            return;
        }

        conversa.setTelefone(telefoneCorreto);
        conversaRepository.save(conversa);
    }

    private void corrigirConversaPorJid(Long idOrganizacao, String jid, String telefoneCorreto) {
        if (!StringUtils.hasText(jid)) {
            return;
        }

        List<WhatsappConversa> conversas = conversaRepository.findAllByIdOrganizacaoAndJid(idOrganizacao, jid);
        if (conversas.isEmpty()) {
            return;
        }

        if (conversas.size() > 1) {
            WhatsappConversa keeper = mesclarConversasDuplicadas(idOrganizacao, conversas);
            if (!telefoneCorreto.equals(keeper.getTelefone())) {
                aplicarCorrecaoTelefone(idOrganizacao, keeper, telefoneCorreto);
            }
            return;
        }

        WhatsappConversa conversa = conversas.get(0);
        if (TelefoneBrasilUtil.celularBrasilComNonoDigito(conversa.getTelefone())) {
            return;
        }
        aplicarCorrecaoTelefone(idOrganizacao, conversa, telefoneCorreto);
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
                StringUtils.hasText(base.dtUltimaMensagem()) ? base.dtUltimaMensagem() : outro.dtUltimaMensagem(),
                StringUtils.hasText(base.ultimaDirecao()) ? base.ultimaDirecao() : outro.ultimaDirecao());
    }

    private Set<String> carregarTelefonesOcultosCanonico(Long idOrganizacao) {
        Set<String> ocultos = carregarTelefonesOcultos(idOrganizacao);
        Set<String> canonico = new HashSet<>();
        for (String telefone : ocultos) {
            canonico.add(normalizarTelefone(telefone));
        }
        return canonico;
    }

    private WhatsappMensagemResponse toMensagemResponseGateway(String telefone, WhatsappMensagemSessaoItemDTO item) {
        LocalDateTime dtEnvio = parseRecebidaEm(item.enviadaEm());
        return new WhatsappMensagemResponse(
                null,
                telefone,
                resolverDirecaoGateway(item.direcao()),
                mapearTipoGateway(item.tipo()),
                item.preview(),
                WhatsappMensagemStatus.DELIVERED,
                item.idMensagemExterna(),
                dtEnvio,
                dtEnvio);
    }

    private List<WhatsappMensagemResponse> complementarHistoricoDaConversa(
            Long idOrganizacao,
            String telefone,
            WhatsappMensagemDirecao direcao) {
        Optional<WhatsappConversa> conversa = buscarConversaOpcional(idOrganizacao, telefone);
        if (conversa.isEmpty() || !StringUtils.hasText(conversa.get().getUltimaMensagem())) {
            return List.of();
        }

        WhatsappMensagemResponse resumo = toMensagemResponseConversa(conversa.get());
        if (direcao != null && !direcao.equals(resumo.direcao())) {
            return List.of();
        }

        return List.of(resumo);
    }

    private WhatsappMensagemResponse toMensagemResponseConversa(WhatsappConversa conversa) {
        WhatsappMensagemDirecao direcao = conversa.getUltimaDirecaoMensagem() != null
                ? conversa.getUltimaDirecaoMensagem()
                : WhatsappMensagemDirecao.INBOUND;
        LocalDateTime data = conversa.getDtUltimaMensagem() != null
                ? conversa.getDtUltimaMensagem()
                : LocalDateTime.now(fusoHorarioAplicacao);

        return new WhatsappMensagemResponse(
                null,
                conversa.getTelefone(),
                direcao,
                mapearTipoGateway(conversa.getTipoUltimaMensagem()),
                conversa.getUltimaMensagem(),
                WhatsappMensagemStatus.DELIVERED,
                null,
                data,
                data);
    }

    private WhatsappMensagemDirecao resolverDirecaoGateway(String direcao) {
        return "OUTBOUND".equalsIgnoreCase(direcao) ? WhatsappMensagemDirecao.OUTBOUND : WhatsappMensagemDirecao.INBOUND;
    }

    private WhatsappMensagemTipo mapearTipoGateway(String tipo) {
        if (!StringUtils.hasText(tipo)) {
            return WhatsappMensagemTipo.TEXT;
        }

        return switch (tipo.toLowerCase()) {
            case "imagem", "image" -> WhatsappMensagemTipo.IMAGE;
            case "documento", "document" -> WhatsappMensagemTipo.DOCUMENT;
            default -> WhatsappMensagemTipo.TEXT;
        };
    }

    private boolean sessaoWhatsappConectada(Long idOrganizacao) {
        return whatsappSessionRepository.findByIdOrganizacao(idOrganizacao)
                .map(sessao -> sessao.getTpStatus() == WhatsappSessionStatus.CONECTADO)
                .orElse(false);
    }

    private boolean sessaoDisponivelParaConversas(Long idOrganizacao) {
        if (sessaoWhatsappConectada(idOrganizacao)) {
            return true;
        }

        StatusWhatsappResposta status = gatewayClient.obterStatus(idOrganizacao);
        if (Boolean.FALSE.equals(status.sucesso())) {
            return false;
        }

        if (Boolean.TRUE.equals(status.conectado())) {
            return true;
        }

        return status.status() != null
                && WhatsappSessionStatus.CONECTADO.name().equalsIgnoreCase(
                        WhatsappGatewayStatusMapper.normalizar(status.status()));
    }

    private void validarSessaoConectada(Long idOrganizacao) {
        if (!sessaoDisponivelParaConversas(idOrganizacao)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "WhatsApp desconectado. Conecte a sessao para acessar conversas.");
        }
    }

    private Optional<WhatsappConversa> obterConversaSessao(Long idOrganizacao, String telefone) {
        String telefoneCanonico = normalizarTelefone(telefone);
        ConcurrentHashMap<String, WhatsappConversa> conversas =
                conversasSessao.get(idOrganizacao);
        if (conversas == null) {
            return Optional.empty();
        }

        WhatsappConversa direta = conversas.get(telefoneCanonico);
        if (direta != null) {
            return Optional.of(direta);
        }

        return conversas.values().stream()
                .filter(conversa -> telefoneCanonico.equals(normalizarTelefone(conversa.getTelefone())))
                .findFirst();
    }

    private void salvarConversaSessao(Long idOrganizacao, String telefone, WhatsappConversa conversa) {
        conversasSessao
                .computeIfAbsent(idOrganizacao, chave -> new ConcurrentHashMap<>())
                .put(normalizarTelefone(telefone), conversa);
    }

    private void removerConversaSessao(Long idOrganizacao, String telefone) {
        ConcurrentHashMap<String, WhatsappConversa> conversas = conversasSessao.get(idOrganizacao);
        if (conversas != null) {
            conversas.remove(normalizarTelefone(telefone));
        }
    }

    private WhatsappConversa atualizarConversaSessao(
            Long idOrganizacao,
            WhatsappInboundRequest request,
            boolean marcarNaoLida) {
        String telefone = normalizarTelefone(request.telefone());
        String preview = request.preview() != null ? request.preview().trim() : null;
        String nome = TelefoneBrasilUtil.resolverNomeContatoWhatsapp(request.nmContato(), telefone);
        LocalDateTime dataMensagem = parseRecebidaEm(request.recebidaEm());
        WhatsappMensagemDirecao direcao = "OUTBOUND".equalsIgnoreCase(request.direcao())
                ? WhatsappMensagemDirecao.OUTBOUND
                : WhatsappMensagemDirecao.INBOUND;

        WhatsappConversa conversa = obterConversaSessao(idOrganizacao, telefone)
                .orElseGet(() -> {
                    WhatsappConversa nova = new WhatsappConversa();
                    nova.setIdOrganizacao(idOrganizacao);
                    nova.setTelefone(telefone);
                    return nova;
                });

        if (conversa.getDtUltimaMensagem() != null && dataMensagem.isBefore(conversa.getDtUltimaMensagem())) {
            salvarConversaSessao(idOrganizacao, telefone, conversa);
            return conversa;
        }

        conversa.setTelefone(telefone);
        if (nome != null) {
            conversa.setNmContato(nome);
        }
        if (StringUtils.hasText(preview)) {
            conversa.setUltimaMensagem(preview);
            conversa.setTipoUltimaMensagem(request.tipo());
            conversa.setUltimaDirecaoMensagem(direcao);
            conversa.setDtUltimaMensagem(dataMensagem);
        }
        if (StringUtils.hasText(request.jid())) {
            conversa.setJid(request.jid());
        }
        if (marcarNaoLida) {
            conversa.setNaoLida(true);
        } else {
            conversa.setNaoLida(false);
        }

        salvarConversaSessao(idOrganizacao, telefone, conversa);
        return conversa;
    }

    private LocalDateTime parseRecebidaEm(String recebidaEm) {
        if (!StringUtils.hasText(recebidaEm)) {
            return LocalDateTime.now(fusoHorarioAplicacao);
        }
        try {
            return Instant.parse(recebidaEm).atZone(fusoHorarioAplicacao).toLocalDateTime();
        } catch (DateTimeParseException ex) {
            try {
                return LocalDateTime.parse(recebidaEm);
            } catch (DateTimeParseException ignored) {
                return LocalDateTime.now(fusoHorarioAplicacao);
            }
        }
    }
}
