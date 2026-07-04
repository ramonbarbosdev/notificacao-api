package com.notificacao_api.service.queue;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.notificacao_api.config.PropriedadesProtecaoNotificacao;
import com.notificacao_api.dto.notificacao.EnviarNotificacaoRequisicao;
import com.notificacao_api.dto.notificacao.EnviarNotificacaoResposta;
import com.notificacao_api.dto.notificacao.FilaNotificacaoResponseDTO;
import com.notificacao_api.dto.notificacao.NotificacaoFilaFilter;
import com.notificacao_api.enums.CanalNotificacao;
import com.notificacao_api.enums.EventoAuditoriaNotificacao;
import com.notificacao_api.enums.StatusNotificacao;
import com.notificacao_api.model.Notificacao;
import com.notificacao_api.repository.NotificacaoRepository;
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
    private final AuditoriaNotificacaoService auditoriaService;
    private final AuditoriaEventoService auditoriaEventoService;
    private final PlanoLimiteService planoLimiteService;
    private final OrganizacaoConfiguracaoService organizacaoConfiguracaoService;
    private final EstimativaTempoEnvioService estimativaTempoEnvioService;
    private final AlertaOperacionalService alertaOperacionalService;
    private final WhatsappSessaoService whatsappSessaoService;

    public FilaNotificacaoService(
            TenantContextService tenantContextService,
            ContatoService contatoService,
            NotificacaoRepository notificacaoRepository,
            ProtecaoNotificacaoService protecaoService,
            PropriedadesProtecaoNotificacao propriedades,
            AuditoriaNotificacaoService auditoriaService,
            AuditoriaEventoService auditoriaEventoService,
            PlanoLimiteService planoLimiteService,
            OrganizacaoConfiguracaoService organizacaoConfiguracaoService,
            EstimativaTempoEnvioService estimativaTempoEnvioService,
            AlertaOperacionalService alertaOperacionalService,
            WhatsappSessaoService whatsappSessaoService) {

        this.tenantContextService = tenantContextService;
        this.contatoService = contatoService;
        this.notificacaoRepository = notificacaoRepository;
        this.protecaoService = protecaoService;
        this.propriedades = propriedades;
        this.auditoriaService = auditoriaService;
        this.auditoriaEventoService = auditoriaEventoService;
        this.planoLimiteService = planoLimiteService;
        this.organizacaoConfiguracaoService = organizacaoConfiguracaoService;
        this.estimativaTempoEnvioService = estimativaTempoEnvioService;
        this.alertaOperacionalService = alertaOperacionalService;
        this.whatsappSessaoService = whatsappSessaoService;
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
                propriedades.maximoTentativas());
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

        return new FilaNotificacaoResponseDTO(
                notificacao.getIdNotificacao(),
                notificacao.getCanal(),
                notificacao.getDestinatario(),
                notificacao.getStatus(),
                notificacao.getProvedor(),
                notificacao.getTentativas(),
                notificacao.getDtProximaTentativa(),
                notificacao.getErro(),
                notificacao.getDtCriacao());
    }
}
