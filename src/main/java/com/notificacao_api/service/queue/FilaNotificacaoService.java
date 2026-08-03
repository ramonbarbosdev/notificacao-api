package com.notificacao_api.service.queue;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.notificacao_api.config.PropriedadesProtecaoNotificacao;
import com.notificacao_api.dto.notificacao.AdminFilaNotificacaoResponseDTO;
import com.notificacao_api.dto.notificacao.AdminNotificacaoDetalheResponseDTO;
import com.notificacao_api.dto.notificacao.AdminNotificacaoFilaFilter;
import com.notificacao_api.dto.notificacao.EnviarNotificacaoRequisicao;
import com.notificacao_api.dto.notificacao.EnviarNotificacaoResposta;
import com.notificacao_api.dto.notificacao.FilaNotificacaoResponseDTO;
import com.notificacao_api.dto.notificacao.FilaResumoResponseDTO;
import com.notificacao_api.dto.notificacao.NotificacaoFilaFilter;
import com.notificacao_api.enums.CanalNotificacao;
import com.notificacao_api.enums.EventoAuditoriaNotificacao;
import com.notificacao_api.enums.StatusNotificacao;
import com.notificacao_api.model.Notificacao;
import com.notificacao_api.model.Organizacao;
import com.notificacao_api.repository.NotificacaoRepository;
import com.notificacao_api.repository.OrganizacaoRepository;
import com.notificacao_api.service.AlertaOperacionalService;
import com.notificacao_api.service.AuditoriaNotificacaoService;
import com.notificacao_api.service.AuditoriaEventoService;
import com.notificacao_api.service.ContatoService;
import com.notificacao_api.service.OrganizacaoConfiguracaoService;
import com.notificacao_api.service.PlanoLimiteService;
import com.notificacao_api.service.TenantContextService;
import com.notificacao_api.service.whatsapp.WhatsappSessaoService;
import com.notificacao_api.shared.GenericSpecificationBuilder;

@Service
public class FilaNotificacaoService {

    private final TenantContextService tenantContextService;
    private final ContatoService contatoService;
    private final NotificacaoRepository notificacaoRepository;
    private final ProtecaoNotificacaoService protecaoService;
    private final PropriedadesProtecaoNotificacao propriedades;
    private final ProtecaoOperacionalConfigResolver protecaoOperacionalConfigResolver;
    private final AuditoriaNotificacaoService auditoriaService;
    private final AuditoriaEventoService auditoriaEventoService;
    private final PlanoLimiteService planoLimiteService;
    private final OrganizacaoConfiguracaoService organizacaoConfiguracaoService;
    private final EstimativaTempoEnvioService estimativaTempoEnvioService;
    private final AlertaOperacionalService alertaOperacionalService;
    private final WhatsappSessaoService whatsappSessaoService;
    private final OrganizacaoRepository organizacaoRepository;
    private final NotificacaoFilaWebSocketService notificacaoFilaWebSocketService;

    public FilaNotificacaoService(
            TenantContextService tenantContextService,
            ContatoService contatoService,
            NotificacaoRepository notificacaoRepository,
            ProtecaoNotificacaoService protecaoService,
            PropriedadesProtecaoNotificacao propriedades,
            ProtecaoOperacionalConfigResolver protecaoOperacionalConfigResolver,
            AuditoriaNotificacaoService auditoriaService,
            AuditoriaEventoService auditoriaEventoService,
            PlanoLimiteService planoLimiteService,
            OrganizacaoConfiguracaoService organizacaoConfiguracaoService,
            EstimativaTempoEnvioService estimativaTempoEnvioService,
            AlertaOperacionalService alertaOperacionalService,
            WhatsappSessaoService whatsappSessaoService,
            OrganizacaoRepository organizacaoRepository,
            NotificacaoFilaWebSocketService notificacaoFilaWebSocketService) {

        this.tenantContextService = tenantContextService;
        this.contatoService = contatoService;
        this.notificacaoRepository = notificacaoRepository;
        this.protecaoService = protecaoService;
        this.propriedades = propriedades;
        this.protecaoOperacionalConfigResolver = protecaoOperacionalConfigResolver;
        this.auditoriaService = auditoriaService;
        this.auditoriaEventoService = auditoriaEventoService;
        this.planoLimiteService = planoLimiteService;
        this.organizacaoConfiguracaoService = organizacaoConfiguracaoService;
        this.estimativaTempoEnvioService = estimativaTempoEnvioService;
        this.alertaOperacionalService = alertaOperacionalService;
        this.whatsappSessaoService = whatsappSessaoService;
        this.organizacaoRepository = organizacaoRepository;
        this.notificacaoFilaWebSocketService = notificacaoFilaWebSocketService;
    }

    @Transactional(readOnly = true)
    public FilaResumoResponseDTO resumoFila() {
        Long idOrganizacao = tenantContextService.idOrganizacaoObrigatoria();
        return montarResumo(idOrganizacao);
    }

    @Transactional(readOnly = true)
    public Page<AdminFilaNotificacaoResponseDTO> listarFilaGlobal(
            AdminNotificacaoFilaFilter filter,
            Pageable pageable) {
        Specification<Notificacao> filterSpec = GenericSpecificationBuilder.byFilter(filter);
        Page<Notificacao> page = notificacaoRepository.findAll(filterSpec, pageable);
        Map<Long, String> nomesOrganizacao = carregarNomesOrganizacao(page.getContent());
        return page.map(notificacao -> toAdminFilaResponse(notificacao, nomesOrganizacao));
    }

    @Transactional(readOnly = true)
    public AdminNotificacaoDetalheResponseDTO obterDetalheGlobal(Long idNotificacao) {
        Notificacao notificacao = carregar(idNotificacao);
        String nmOrganizacao = organizacaoRepository.findById(notificacao.getIdOrganizacao())
                .map(Organizacao::getNmOrganizacao)
                .orElse("Organizacao #" + notificacao.getIdOrganizacao());
        return toAdminDetalheResponse(notificacao, nmOrganizacao);
    }

    @Transactional
    public AdminNotificacaoDetalheResponseDTO reenviarManualGlobal(Long idNotificacao) {
        Notificacao notificacao = reenviarManual(idNotificacao, null);
        String nmOrganizacao = organizacaoRepository.findById(notificacao.getIdOrganizacao())
                .map(Organizacao::getNmOrganizacao)
                .orElse("Organizacao #" + notificacao.getIdOrganizacao());
        return toAdminDetalheResponse(notificacao, nmOrganizacao);
    }

    @Transactional
    public AdminNotificacaoDetalheResponseDTO cancelarManualGlobal(Long idNotificacao, String motivo) {
        Notificacao notificacao = cancelarManual(idNotificacao, null, motivo);
        String nmOrganizacao = organizacaoRepository.findById(notificacao.getIdOrganizacao())
                .map(Organizacao::getNmOrganizacao)
                .orElse("Organizacao #" + notificacao.getIdOrganizacao());
        return toAdminDetalheResponse(notificacao, nmOrganizacao);
    }

    @Transactional
    public FilaNotificacaoResponseDTO reenviarManualDaOrganizacao(Long idNotificacao) {
        Long idOrganizacao = tenantContextService.idOrganizacaoObrigatoria();
        return toFilaResponse(reenviarManual(idNotificacao, idOrganizacao));
    }

    @Transactional
    public Notificacao reenviarManual(Long idNotificacao, Long idOrganizacaoEsperada) {
        Notificacao notificacao = carregar(idNotificacao);
        validarOrganizacao(notificacao, idOrganizacaoEsperada);
        validarReenviavel(notificacao);

        notificacao.setStatus(StatusNotificacao.PENDENTE);
        notificacao.setTentativas(0);
        notificacao.setErro(null);
        notificacao.setDtProximaTentativa(protecaoService.agora());
        notificacao.setDtUltimoProcessamento(null);
        notificacao.setProvedor(null);
        notificacao.setDtEnvio(null);

        Notificacao salva = notificacaoRepository.save(notificacao);
        auditoriaService.registrar(salva, EventoAuditoriaNotificacao.REENVIO_AGENDADO, "Reenvio manual solicitado.");
        registrarEventoSistema(salva, "REENVIO_MANUAL", "Reenvio manual solicitado.");
        notificarAtualizacaoFila(salva);
        return salva;
    }

    @Transactional
    public Notificacao cancelarManual(Long idNotificacao, Long idOrganizacaoEsperada, String motivo) {
        Notificacao notificacao = carregar(idNotificacao);
        validarOrganizacao(notificacao, idOrganizacaoEsperada);

        if (notificacao.getStatus() == StatusNotificacao.ENVIADA
                || notificacao.getStatus() == StatusNotificacao.ENTREGUE
                || notificacao.getStatus() == StatusNotificacao.LIDA) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Notificacoes ja enviadas nao podem ser canceladas.");
        }

        String descricao = motivo == null || motivo.isBlank()
                ? "Cancelamento manual solicitado."
                : motivo.trim();

        notificacao.setStatus(StatusNotificacao.CANCELADA);
        notificacao.setErro(descricao);
        Notificacao salva = notificacaoRepository.save(notificacao);
        auditoriaService.registrar(salva, EventoAuditoriaNotificacao.CANCELADA, descricao);
        registrarEventoSistema(salva, "CANCELADA", descricao);
        notificarAtualizacaoFila(salva);
        return salva;
    }

    private void validarOrganizacao(Notificacao notificacao, Long idOrganizacaoEsperada) {
        if (idOrganizacaoEsperada != null && !idOrganizacaoEsperada.equals(notificacao.getIdOrganizacao())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Notificacao nao encontrada.");
        }
    }

    private void validarReenviavel(Notificacao notificacao) {
        if (notificacao.getStatus() != StatusNotificacao.FALHOU
                && notificacao.getStatus() != StatusNotificacao.BLOQUEADA
                && notificacao.getStatus() != StatusNotificacao.CANCELADA) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Somente notificacoes com falha, bloqueadas ou canceladas podem ser reenviadas.");
        }
    }

    private Map<Long, String> carregarNomesOrganizacao(List<Notificacao> notificacoes) {
        Set<Long> ids = notificacoes.stream()
                .map(Notificacao::getIdOrganizacao)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> nomes = new HashMap<>();
        organizacaoRepository.findAllById(ids)
                .forEach(org -> nomes.put(org.getIdOrganizacao(), org.getNmOrganizacao()));
        return nomes;
    }

    @Transactional(readOnly = true)
    public Page<FilaNotificacaoResponseDTO> listarFila(
            NotificacaoFilaFilter filter,
            Pageable pageable) {

        Long idOrganizacao = tenantContextService.idOrganizacaoObrigatoria();

        Specification<Notificacao> tenantSpec = (root, query, cb) ->
                cb.equal(root.get("idOrganizacao"), idOrganizacao);

        Specification<Notificacao> filterSpec = GenericSpecificationBuilder.byFilter(filter);

        return notificacaoRepository
                .findAll(tenantSpec.and(filterSpec), pageable)
                .map(this::toFilaResponse);
    }

    @Transactional
    public EnviarNotificacaoResposta enfileirar(
            EnviarNotificacaoRequisicao requisicao) {

        Long idOrganizacao = tenantContextService.idOrganizacaoObrigatoria();
        planoLimiteService.validarEnvioNotificacao(idOrganizacao, requisicao.canal());

        if (requisicao.canal() == CanalNotificacao.WHATSAPP) {
            try {
                whatsappSessaoService.validarConectadoParaEnvio(idOrganizacao);
                if (organizacaoConfiguracaoService.exigeConsentimento(idOrganizacao)) {
                    contatoService.validarEnvioAutorizado(
                            idOrganizacao,
                            requisicao.canal(),
                            requisicao.destinatario());
                } else {
                    contatoService.validarNaoBloqueado(
                            idOrganizacao,
                            requisicao.canal(),
                            requisicao.destinatario());
                }
            } catch (ResponseStatusException ex) {
                registrarEventoRequisicao(
                        idOrganizacao,
                        "ENVIO_NEGADO",
                        ex.getReason(),
                        requisicao,
                        null);
                throw ex;
            }
        }

        String hashDeduplicacao = protecaoService.gerarHashDeduplicacao(
                idOrganizacao,
                requisicao.canal(),
                requisicao.destinatario(),
                requisicao.mensagem());

        if (protecaoService.existeDuplicidadeRecente(
                idOrganizacao,
                requisicao.canal(),
                requisicao.destinatario(),
                hashDeduplicacao)) {

            Notificacao bloqueada = criarNotificacao(
                    idOrganizacao,
                    requisicao,
                    hashDeduplicacao);

            bloqueada.setStatus(StatusNotificacao.BLOQUEADA);
            bloqueada.setErro(
                    "Mensagem duplicada bloqueada pela janela de seguranca.");

            bloqueada = notificacaoRepository.save(bloqueada);

            auditoriaService.registrar(
                    bloqueada,
                    EventoAuditoriaNotificacao.BLOQUEADA,
                    bloqueada.getErro());

            registrarEventoSistema(
                    bloqueada,
                    "BLOQUEADA",
                    bloqueada.getErro());

            notificarAtualizacaoFila(bloqueada);
            return resposta(bloqueada);
        }

        Notificacao notificacao = criarNotificacao(
                idOrganizacao,
                requisicao,
                hashDeduplicacao);

        notificacao = notificacaoRepository.save(notificacao);

        auditoriaService.registrar(
                notificacao,
                EventoAuditoriaNotificacao.ENFILEIRADA,
                null);

        notificarAtualizacaoFila(notificacao);
        return resposta(notificacao);
    }

    public void validarTamanhoLote(int tamanho) {
        if (tamanho > propriedades.tamanhoMaximoLote()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Lote excede o limite operacional de "
                            + propriedades.tamanhoMaximoLote()
                            + " mensagens.");
        }
    }

    @Transactional
    public List<Notificacao> buscarPendentesParaProcessar() {
        return notificacaoRepository.buscarPendentesParaProcessar(
                protecaoService.agora(),
                propriedades.tamanhoLoteAgendador());
    }

    @Transactional
    public boolean marcarProcessando(Long idNotificacao) {

        int atualizadas = notificacaoRepository.marcarProcessandoSePendente(
                idNotificacao,
                protecaoService.agora());

        if (atualizadas != 1) {
            return false;
        }

        Notificacao notificacao = carregar(idNotificacao);

        auditoriaService.registrar(
                notificacao,
                EventoAuditoriaNotificacao.PROCESSANDO,
                null);

        notificarAtualizacaoFila(notificacao);
        return true;
    }

    @Transactional
    public Notificacao carregar(Long idNotificacao) {

        return notificacaoRepository.findById(idNotificacao)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Notificacao nao encontrada"));
    }

    @Transactional
    public void reagendar(
            Notificacao notificacao,
            LocalDateTime quando,
            String motivo) {

        Notificacao atual = carregar(notificacao.getIdNotificacao());

        atual.setStatus(StatusNotificacao.PENDENTE);
        atual.setErro(motivo);
        atual.setDtProximaTentativa(quando);

        notificacaoRepository.save(atual);

        auditoriaService.registrar(
                atual,
                EventoAuditoriaNotificacao.REENVIO_AGENDADO,
                motivo);

        registrarEventoSistema(
                atual,
                "REENVIO_AGENDADO",
                motivo);

        try {
            alertaOperacionalService.registrarBloqueioProtecaoFila(atual, motivo);
        } catch (Exception ex) {
            // nao interrompe o fluxo da fila
        }

        notificarAtualizacaoFila(atual);
    }

    @Transactional
    public void marcarEnviada(
            Notificacao notificacao,
            String provedor) {

        Notificacao atual = carregar(notificacao.getIdNotificacao());

        atual.setStatus(StatusNotificacao.ENVIADA);
        atual.setProvedor(provedor);
        atual.setErro(null);
        atual.setDtEnvio(protecaoService.agora());

        notificacaoRepository.save(atual);

        auditoriaService.registrar(
                atual,
                EventoAuditoriaNotificacao.ENVIADA,
                null);

        notificarAtualizacaoFila(atual);
    }

    @Transactional
    public void marcarFalha(
            Notificacao notificacao,
            String erro) {

        marcarFalha(notificacao, erro, true);
    }

    @Transactional
    public void marcarFalha(
            Notificacao notificacao,
            String erro,
            boolean reenviavel) {

        Notificacao atual = carregar(notificacao.getIdNotificacao());

        int tentativas = atual.getTentativas() == null
                ? 1
                : atual.getTentativas() + 1;

        atual.setTentativas(tentativas);
        atual.setErro(erro);

        if (!reenviavel ||
                tentativas >= atual.getTentativasMaximas()) {

            atual.setStatus(StatusNotificacao.FALHOU);

            notificacaoRepository.save(atual);

            auditoriaService.registrar(
                    atual,
                    EventoAuditoriaNotificacao.FALHOU,
                    erro);

            registrarEventoSistema(
                    atual,
                    "FALHOU",
                    erro);

            try {
                alertaOperacionalService.registrarFalhaFila(atual, erro);
            } catch (Exception ex) {
                // nao interrompe o fluxo da fila
            }

            notificarAtualizacaoFila(atual);
            return;
        }

        atual.setStatus(StatusNotificacao.PENDENTE);

        atual.setDtProximaTentativa(
                protecaoService.calcularProximaTentativa(
                        tentativas));

        notificacaoRepository.save(atual);

        auditoriaService.registrar(
                atual,
                EventoAuditoriaNotificacao.REENVIO_AGENDADO,
                erro);

        registrarEventoSistema(
                atual,
                "REENVIO_AGENDADO",
                erro);

        notificarAtualizacaoFila(atual);
    }

    private void registrarEventoRequisicao(
            Long idOrganizacao,
            String acao,
            String descricao,
            EnviarNotificacaoRequisicao requisicao,
            Long idNotificacao) {
        auditoriaEventoService.registrar(
                idOrganizacao,
                "NOTIFICACAO",
                acao,
                descricao,
                null,
                dadosAuditoria(requisicao, idNotificacao, descricao));
    }

    private void registrarEventoSistema(
            Notificacao notificacao,
            String acao,
            String descricao) {
        auditoriaEventoService.registrarSistema(
                notificacao.getIdOrganizacao(),
                "NOTIFICACAO",
                acao,
                descricao,
                null,
                dadosAuditoria(notificacao, descricao));
    }

    private Map<String, Object> dadosAuditoria(
            EnviarNotificacaoRequisicao requisicao,
            Long idNotificacao,
            String motivo) {
        Map<String, Object> dados = new LinkedHashMap<>();
        dados.put("idNotificacao", idNotificacao);
        dados.put("canal", requisicao.canal());
        dados.put("destinatario", requisicao.destinatario());
        dados.put("assunto", requisicao.assunto());
        dados.put("motivo", motivo);
        return dados;
    }

    private Map<String, Object> dadosAuditoria(
            Notificacao notificacao,
            String motivo) {
        Map<String, Object> dados = new LinkedHashMap<>();
        dados.put("idNotificacao", notificacao.getIdNotificacao());
        dados.put("canal", notificacao.getCanal());
        dados.put("destinatario", notificacao.getDestinatario());
        dados.put("status", notificacao.getStatus());
        dados.put("tentativas", notificacao.getTentativas());
        dados.put("motivo", motivo);
        return dados;
    }

    private Notificacao criarNotificacao(
            Long idOrganizacao,
            EnviarNotificacaoRequisicao requisicao,
            String hashDeduplicacao) {

        Notificacao notificacao = new Notificacao();

        notificacao.setIdOrganizacao(idOrganizacao);
        notificacao.setCanal(requisicao.canal());
        notificacao.setDestinatario(requisicao.destinatario());
        notificacao.setAssunto(requisicao.assunto());
        notificacao.setMensagem(requisicao.mensagem());
        notificacao.setStatus(StatusNotificacao.PENDENTE);
        notificacao.setTentativasMaximas(
                protecaoOperacionalConfigResolver.tentativasMaximasNotificacao(idOrganizacao));
        notificacao.setHashDeduplicacao(hashDeduplicacao);

        notificacao.setDtProximaTentativa(
                protecaoService.agora());

        return notificacao;
    }

    private EnviarNotificacaoResposta resposta(
            Notificacao notificacao) {

        var estimativa = estimativaTempoEnvioService.calcular(notificacao);

        return new EnviarNotificacaoResposta(
                notificacao.getStatus() != StatusNotificacao.BLOQUEADA,
                notificacao.getIdNotificacao(),
                notificacao.getCanal(),
                notificacao.getStatus(),
                notificacao.getErro(),
                estimativa.tempoEstimadoEnvioSegundos(),
                estimativa.posicaoFila(),
                estimativa.tempoEstimadoEnvioTexto());
    }

    private FilaNotificacaoResponseDTO toFilaResponse(
            Notificacao notificacao) {
        RetomadaEnvio retomada = calcularRetomada(notificacao);
        EstimativaEnvio estimativa = resolverEstimativa(notificacao);
        return new FilaNotificacaoResponseDTO(
                notificacao.getIdNotificacao(),
                notificacao.getCanal(),
                notificacao.getDestinatario(),
                notificacao.getStatus(),
                notificacao.getProvedor(),
                notificacao.getTentativas(),
                notificacao.getDtProximaTentativa(),
                notificacao.getErro(),
                notificacao.getDtCriacao(),
                notificacao.getTentativasMaximas(),
                notificacao.getDtEnvio(),
                retomada.em(),
                retomada.texto(),
                estimativa.segundos(),
                estimativa.posicaoFila(),
                estimativa.texto(),
                estimativa.previsaoEm());
    }

    private AdminFilaNotificacaoResponseDTO toAdminFilaResponse(
            Notificacao notificacao,
            Map<Long, String> nomesOrganizacao) {
        RetomadaEnvio retomada = calcularRetomada(notificacao);
        return new AdminFilaNotificacaoResponseDTO(
                notificacao.getIdNotificacao(),
                notificacao.getIdOrganizacao(),
                nomesOrganizacao.getOrDefault(notificacao.getIdOrganizacao(), "Organizacao #" + notificacao.getIdOrganizacao()),
                notificacao.getCanal(),
                notificacao.getDestinatario(),
                notificacao.getAssunto(),
                resumirMensagem(notificacao.getMensagem()),
                notificacao.getStatus(),
                notificacao.getProvedor(),
                notificacao.getTentativas(),
                notificacao.getTentativasMaximas(),
                notificacao.getDtProximaTentativa(),
                notificacao.getDtEnvio(),
                retomada.em(),
                retomada.texto(),
                notificacao.getErro(),
                notificacao.getDtCriacao(),
                notificacao.getDtAtualizacao());
    }

    private AdminNotificacaoDetalheResponseDTO toAdminDetalheResponse(
            Notificacao notificacao,
            String nmOrganizacao) {
        RetomadaEnvio retomada = calcularRetomada(notificacao);
        return new AdminNotificacaoDetalheResponseDTO(
                notificacao.getIdNotificacao(),
                notificacao.getIdOrganizacao(),
                nmOrganizacao,
                notificacao.getCanal(),
                notificacao.getDestinatario(),
                notificacao.getAssunto(),
                notificacao.getMensagem(),
                notificacao.getStatus(),
                notificacao.getProvedor(),
                notificacao.getTentativas(),
                notificacao.getTentativasMaximas(),
                notificacao.getDtProximaTentativa(),
                notificacao.getDtEnvio(),
                retomada.em(),
                retomada.texto(),
                notificacao.getErro(),
                notificacao.getDtCriacao(),
                notificacao.getDtAtualizacao());
    }

    private RetomadaEnvio calcularRetomada(Notificacao notificacao) {
        if (notificacao.getStatus() != StatusNotificacao.PENDENTE
                && notificacao.getStatus() != StatusNotificacao.PROCESSANDO) {
            return RetomadaEnvio.vazio();
        }

        LocalDateTime agora = protecaoService.agora();
        LocalDateTime retomada = notificacao.getDtProximaTentativa();
        if (retomada == null || !retomada.isAfter(agora)) {
            return RetomadaEnvio.vazio();
        }

        long segundos = Duration.between(agora, retomada).getSeconds();
        return new RetomadaEnvio(retomada, EstimativaTempoEnvioService.formatarTempo(segundos));
    }

    private String resumirMensagem(String mensagem) {
        if (mensagem == null || mensagem.isBlank()) {
            return null;
        }
        String texto = mensagem.trim();
        return texto.length() <= 120 ? texto : texto.substring(0, 120) + "...";
    }

    private record RetomadaEnvio(LocalDateTime em, String texto) {
        static RetomadaEnvio vazio() {
            return new RetomadaEnvio(null, null);
        }
    }

    private record EstimativaEnvio(
            Long segundos,
            Integer posicaoFila,
            String texto,
            LocalDateTime previsaoEm) {

        static EstimativaEnvio vazio() {
            return new EstimativaEnvio(null, null, null, null);
        }
    }

    private EstimativaEnvio resolverEstimativa(Notificacao notificacao) {
        if (notificacao.getStatus() != StatusNotificacao.PENDENTE
                && notificacao.getStatus() != StatusNotificacao.PROCESSANDO) {
            return EstimativaEnvio.vazio();
        }

        var estimativa = estimativaTempoEnvioService.calcular(notificacao);
        if (estimativa.tempoEstimadoEnvioSegundos() == null) {
            return EstimativaEnvio.vazio();
        }

        LocalDateTime previsaoEm = protecaoService.agora()
                .plusSeconds(estimativa.tempoEstimadoEnvioSegundos());

        return new EstimativaEnvio(
                estimativa.tempoEstimadoEnvioSegundos(),
                estimativa.posicaoFila(),
                estimativa.tempoEstimadoEnvioTexto(),
                previsaoEm);
    }

    private FilaResumoResponseDTO montarResumo(Long idOrganizacao) {
        Map<StatusNotificacao, Long> contagem = notificacaoRepository.contarPorStatus(idOrganizacao)
                .stream()
                .collect(Collectors.toMap(
                        row -> (StatusNotificacao) row[0],
                        row -> (Long) row[1]));

        long enviada = contagem.getOrDefault(StatusNotificacao.ENVIADA, 0L)
                + contagem.getOrDefault(StatusNotificacao.ENTREGUE, 0L)
                + contagem.getOrDefault(StatusNotificacao.LIDA, 0L);

        Optional<Notificacao> proxima = notificacaoRepository
                .findFirstByIdOrganizacaoAndStatusInOrderByDtProximaTentativaAsc(
                        idOrganizacao,
                        List.of(StatusNotificacao.PENDENTE, StatusNotificacao.PROCESSANDO));

        EstimativaEnvio proximaEstimativa = proxima.map(this::resolverEstimativa).orElse(EstimativaEnvio.vazio());

        return new FilaResumoResponseDTO(
                contagem.getOrDefault(StatusNotificacao.PENDENTE, 0L),
                contagem.getOrDefault(StatusNotificacao.PROCESSANDO, 0L),
                enviada,
                contagem.getOrDefault(StatusNotificacao.FALHOU, 0L),
                contagem.getOrDefault(StatusNotificacao.BLOQUEADA, 0L),
                proximaEstimativa.texto(),
                proximaEstimativa.previsaoEm(),
                protecaoService.agora());
    }

    private void notificarAtualizacaoFila(Notificacao notificacao) {
        if (notificacao == null || notificacao.getIdOrganizacao() == null) {
            return;
        }

        notificacaoFilaWebSocketService.publicarAtualizacao(
                notificacao.getIdOrganizacao(),
                notificacao.getIdNotificacao(),
                notificacao.getStatus(),
                montarResumo(notificacao.getIdOrganizacao()));
    }
}
