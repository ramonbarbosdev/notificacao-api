package com.notificacao_api.service.whatsapp;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import com.notificacao_api.dto.whatsapp.EnviarMensagemWhatsappRequisicao;
import com.notificacao_api.dto.whatsapp.EnviarMensagemWhatsappResposta;
import com.notificacao_api.dto.whatsapp.GatewaySessoesListaResponseDTO;
import com.notificacao_api.dto.whatsapp.StatusWhatsappResposta;
import com.notificacao_api.enums.WhatsappSessionStatus;
import com.notificacao_api.exception.WhatsappNaoConectadoException;
import com.notificacao_api.model.WhatsappSession;
import com.notificacao_api.repository.WhatsappSessionRepository;
import com.notificacao_api.service.ContatoService;
import com.notificacao_api.service.TenantContextService;

import jakarta.annotation.PreDestroy;

@Service
public class WhatsappSessaoService {

    private final TenantContextService tenantContextService;
    private final WhatsAppGatewayClient gatewayClient;
    private final WhatsappSessionRepository whatsappSessionRepository;
    private final WhatsappConexaoWebSocketService webSocketService;
    private final WhatsappSessaoOperacionalService sessaoOperacionalService;
    private final ContatoService contatoService;
    private final long cooldownConexaoSegundos;
    private final TransactionTemplate transactionTemplate;
    private final ConcurrentMap<Long, Object> locksPorOrganizacao = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, ScheduledFuture<?>> sincronizacaoStatusAtiva = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable);
        thread.setName("whatsapp-conexao-cooldown");
        thread.setDaemon(true);
        return thread;
    });

    public WhatsappSessaoService(
            TenantContextService tenantContextService,
            WhatsAppGatewayClient gatewayClient,
            WhatsappSessionRepository whatsappSessionRepository,
            WhatsappConexaoWebSocketService webSocketService,
            WhatsappSessaoOperacionalService sessaoOperacionalService,
            ContatoService contatoService,
            PlatformTransactionManager transactionManager,
            @Value("${whatsapp.conexao.cooldown-segundos:30}") long cooldownConexaoSegundos) {
        this.tenantContextService = tenantContextService;
        this.gatewayClient = gatewayClient;
        this.whatsappSessionRepository = whatsappSessionRepository;
        this.webSocketService = webSocketService;
        this.sessaoOperacionalService = sessaoOperacionalService;
        this.contatoService = contatoService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.cooldownConexaoSegundos = cooldownConexaoSegundos;
    }

    @PreDestroy
    void destruir() {
        scheduler.shutdownNow();
    }

    @Transactional
    public StatusWhatsappResposta conectar() {
        Long idOrganizacao = tenantContextService.idOrganizacaoObrigatoria();
        Object lock = locksPorOrganizacao.computeIfAbsent(idOrganizacao, chave -> new Object());

        synchronized (lock) {
            validarCooldownConexao(idOrganizacao);
            salvarStatusConectando(idOrganizacao);
            publicarTentativaIniciada(idOrganizacao);
            agendarLiberacaoConexao(idOrganizacao);

            StatusWhatsappResposta resposta = gatewayClient.conectar(idOrganizacao);
            salvarStatus(idOrganizacao, resposta);
            publicarStatusAtual(idOrganizacao, resposta);
            iniciarSincronizacaoStatus(idOrganizacao);
            return enriquecer(idOrganizacao, resposta);
        }
    }

    @Transactional
    public StatusWhatsappResposta obterStatus() {
        Long idOrganizacao = tenantContextService.idOrganizacaoObrigatoria();
        StatusWhatsappResposta resposta = gatewayClient.obterStatus(idOrganizacao);
        salvarStatus(idOrganizacao, resposta);
        publicarStatusAtual(idOrganizacao, resposta);
        return enriquecer(idOrganizacao, resposta);
    }

    @Transactional
    public StatusWhatsappResposta reativarOperacao() {
        Long idOrganizacao = tenantContextService.idOrganizacaoObrigatoria();
        sessaoOperacionalService.reativarOperacao(idOrganizacao);
        return obterStatus();
    }

    @Transactional
    public StatusWhatsappResposta sincronizarGatewayOrganizacao(
            Long idOrganizacao,
            Long idOrganizacaoAnterior) {
        if (idOrganizacaoAnterior != null && !idOrganizacaoAnterior.equals(idOrganizacao)) {
            StatusWhatsappResposta migracao = gatewayClient.atualizarOrganizacao(
                    idOrganizacao,
                    idOrganizacaoAnterior);
            validarRespostaGateway(idOrganizacao, migracao, "migrar organizacao no gateway");
        } else {
            StatusWhatsappResposta atualizacao = gatewayClient.atualizarOrganizacao(idOrganizacao, null);
            validarRespostaGateway(idOrganizacao, atualizacao, "atualizar organizacao no gateway");
        }

        StatusWhatsappResposta resposta = gatewayClient.obterStatus(idOrganizacao);
        salvarStatus(idOrganizacao, resposta);
        return enriquecer(idOrganizacao, resposta);
    }

    @Transactional(readOnly = true)
    public GatewaySessoesListaResponseDTO listarSessoesGateway() {
        return gatewayClient.listarSessoes();
    }

    private void validarRespostaGateway(
            Long idOrganizacao,
            StatusWhatsappResposta resposta,
            String acao) {
        if (Boolean.FALSE.equals(resposta == null ? null : resposta.sucesso())) {
            String mensagem = resposta != null && resposta.erro() != null && !resposta.erro().isBlank()
                    ? resposta.erro()
                    : "Falha ao " + acao + " para organizacao " + idOrganizacao + ".";
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, mensagem);
        }
    }

    public EnviarMensagemWhatsappResposta enviarMensagem(EnviarMensagemWhatsappRequisicao requisicao) {
        Long idOrganizacao = tenantContextService.idOrganizacaoObrigatoria();
        return enviarMensagemDaOrganizacao(idOrganizacao, requisicao);
    }

    public EnviarMensagemWhatsappResposta enviarMensagemDaOrganizacao(
            Long idOrganizacao,
            EnviarMensagemWhatsappRequisicao requisicao) {
        return gatewayClient.enviarMensagem(idOrganizacao, requisicao);
    }

    public void validarConectadoParaEnvio(Long idOrganizacao) {
        StatusWhatsappResposta status = gatewayClient.obterStatus(idOrganizacao);
        if (Boolean.TRUE.equals(status.conectado())) {
            return;
        }
        throw new WhatsappNaoConectadoException();
    }

    @Transactional
    public StatusWhatsappResposta desconectar() {
        Long idOrganizacao = tenantContextService.idOrganizacaoObrigatoria();
        return limparSessaoOrganizacao(
                idOrganizacao,
                "Sessao desconectada. Arquivos e tokens locais foram removidos. Conecte novamente para escanear o QR Code.");
    }

    private StatusWhatsappResposta limparSessaoOrganizacao(Long idOrganizacao, String mensagem) {
        cancelarSincronizacaoStatus(idOrganizacao);
        contatoService.limparContatosSincronizadosWhatsapp(idOrganizacao);
        gatewayClient.desconectar(idOrganizacao);
        sessaoOperacionalService.reativarOperacao(idOrganizacao);

        WhatsappSession sessao = whatsappSessionRepository.findByIdOrganizacao(idOrganizacao)
                .orElseGet(() -> novaSessao(idOrganizacao));
        sessao.setTpStatus(WhatsappSessionStatus.NAO_INICIADO);
        sessao.setNuTelefone(null);
        sessao.setDsSessionPath("organizacao-" + idOrganizacao);
        whatsappSessionRepository.save(sessao);

        StatusWhatsappResposta resposta = StatusWhatsappResposta.respostaGateway(
                true,
                idOrganizacao,
                WhatsappSessionStatus.NAO_INICIADO.name(),
                false,
                null,
                null,
                null,
                mensagem);
        publicarConexaoCancelada(idOrganizacao, resposta);
        return enriquecer(idOrganizacao, resposta);
    }

    private StatusWhatsappResposta enriquecer(Long idOrganizacao, StatusWhatsappResposta resposta) {
        return sessaoOperacionalService.enriquecer(idOrganizacao, resposta);
    }

    private WhatsappSession salvarStatus(Long idOrganizacao, StatusWhatsappResposta resposta) {
        WhatsappSession sessao = whatsappSessionRepository.findByIdOrganizacao(idOrganizacao)
                .orElseGet(() -> novaSessao(idOrganizacao));

        sessao.setTpStatus(statusDaResposta(resposta));
        sessao.setNuTelefone(resposta == null ? null : resposta.telefone());
        sessao.setDsSessionPath("organizacao-" + idOrganizacao);

        if (Boolean.TRUE.equals(resposta == null ? null : resposta.conectado())) {
            sessao.setDtUltimaConexao(LocalDateTime.now());
        }

        return whatsappSessionRepository.save(sessao);
    }

    private void salvarStatusConectando(Long idOrganizacao) {
        WhatsappSession sessao = whatsappSessionRepository.findByIdOrganizacao(idOrganizacao)
                .orElseGet(() -> novaSessao(idOrganizacao));

        sessao.setTpStatus(WhatsappSessionStatus.CONECTANDO);
        sessao.setDsSessionPath("organizacao-" + idOrganizacao);

        whatsappSessionRepository.saveAndFlush(sessao);
    }

    private void validarCooldownConexao(Long idOrganizacao) {
        whatsappSessionRepository.findByIdOrganizacao(idOrganizacao)
                .filter(this::tentativaDeConexaoEmAndamento)
                .ifPresent(this::bloquearSeAindaEstaNoCooldown);
    }

    private boolean tentativaDeConexaoEmAndamento(WhatsappSession sessao) {
        return sessao.getTpStatus() == WhatsappSessionStatus.CONECTANDO
                || sessao.getTpStatus() == WhatsappSessionStatus.AGUARDANDO_QR;
    }

    private void bloquearSeAindaEstaNoCooldown(WhatsappSession sessao) {
        long segundosDecorridos = Duration.between(sessao.getDtAtualizacao(), LocalDateTime.now()).toSeconds();
        long segundosRestantes = cooldownConexaoSegundos - segundosDecorridos;

        if (segundosRestantes > 0) {
            webSocketService.publicar(
                    sessao.getIdOrganizacao(),
                    "TENTATIVA_BLOQUEADA",
                    sessao.getTpStatus().name(),
                    false,
                    segundosRestantes,
                    "Conexao WhatsApp em andamento.");

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Conexao WhatsApp em andamento. Aguarde " + segundosRestantes
                            + " segundos ou cancele a conexao atual.");
        }
    }

    private void publicarTentativaIniciada(Long idOrganizacao) {
        webSocketService.publicar(
                idOrganizacao,
                "TENTATIVA_INICIADA",
                WhatsappSessionStatus.CONECTANDO.name(),
                false,
                cooldownConexaoSegundos,
                "Tentativa de conexao WhatsApp iniciada.");
    }

    private void publicarStatusAtual(Long idOrganizacao, StatusWhatsappResposta resposta) {
        if (resposta == null) {
            return;
        }

        boolean tentativaEmAndamento = WhatsappGatewayStatusMapper.emAndamento(resposta.status());
        webSocketService.publicar(
                idOrganizacao,
                "STATUS_ATUALIZADO",
                resposta.status(),
                !tentativaEmAndamento,
                tentativaEmAndamento ? cooldownConexaoSegundos : 0L,
                resposta.erro());
    }

    private void iniciarSincronizacaoStatus(Long idOrganizacao) {
        cancelarSincronizacaoStatus(idOrganizacao);

        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(
                () -> sincronizarStatusOrganizacao(idOrganizacao),
                3,
                3,
                TimeUnit.SECONDS);
        sincronizacaoStatusAtiva.put(idOrganizacao, future);

        scheduler.schedule(
                () -> cancelarSincronizacaoStatus(idOrganizacao),
                2,
                TimeUnit.MINUTES);
    }

    private void cancelarSincronizacaoStatus(Long idOrganizacao) {
        ScheduledFuture<?> future = sincronizacaoStatusAtiva.remove(idOrganizacao);
        if (future != null) {
            future.cancel(false);
        }
    }

    private void sincronizarStatusOrganizacao(Long idOrganizacao) {
        transactionTemplate.executeWithoutResult(status -> {
            StatusWhatsappResposta resposta = gatewayClient.obterStatus(idOrganizacao);
            if (resposta == null || Boolean.FALSE.equals(resposta.sucesso())) {
                return;
            }

            WhatsappSession anterior = whatsappSessionRepository.findByIdOrganizacao(idOrganizacao).orElse(null);
            WhatsappSessionStatus statusAnterior = anterior == null ? null : anterior.getTpStatus();
            String telefoneAnterior = anterior == null ? null : anterior.getNuTelefone();

            WhatsappSession salva = salvarStatus(idOrganizacao, resposta);
            boolean conectado = Boolean.TRUE.equals(resposta.conectado());
            boolean mudou = statusAnterior != salva.getTpStatus()
                    || (telefoneAnterior == null ? resposta.telefone() != null
                            : !telefoneAnterior.equals(resposta.telefone()));

            if (mudou || WhatsappGatewayStatusMapper.emAndamento(resposta.status())) {
                publicarStatusAtual(idOrganizacao, enriquecer(idOrganizacao, resposta));
            }

            if (conectado || !WhatsappGatewayStatusMapper.emAndamento(resposta.status())) {
                cancelarSincronizacaoStatus(idOrganizacao);
            }
        });
    }

    private void publicarConexaoCancelada(Long idOrganizacao, StatusWhatsappResposta resposta) {
        webSocketService.publicar(
                idOrganizacao,
                "CONEXAO_CANCELADA",
                resposta == null ? WhatsappSessionStatus.DESCONECTADO.name() : resposta.status(),
                true,
                0L,
                "Tentativa de conexao WhatsApp cancelada.");
    }

    private void agendarLiberacaoConexao(Long idOrganizacao) {
        scheduler.schedule(
                () -> publicarLiberacaoSeNecessaria(idOrganizacao),
                cooldownConexaoSegundos,
                TimeUnit.SECONDS);
    }

    private void publicarLiberacaoSeNecessaria(Long idOrganizacao) {
        whatsappSessionRepository.findByIdOrganizacao(idOrganizacao)
                .filter(this::tentativaDeConexaoEmAndamento)
                .ifPresent(sessao -> webSocketService.publicar(
                        idOrganizacao,
                        "CONEXAO_LIBERADA",
                        sessao.getTpStatus().name(),
                        true,
                        0L,
                        "Nova tentativa de conexao WhatsApp liberada."));
    }

    private boolean statusEmAndamento(String status) {
        return WhatsappGatewayStatusMapper.emAndamento(status);
    }

    private WhatsappSessionStatus statusDaResposta(StatusWhatsappResposta resposta) {
        if (resposta == null || Boolean.FALSE.equals(resposta.sucesso())) {
            return WhatsappSessionStatus.ERRO;
        }

        if (resposta.status() == null || resposta.status().isBlank()) {
            return Boolean.TRUE.equals(resposta.conectado())
                    ? WhatsappSessionStatus.CONECTADO
                    : WhatsappSessionStatus.NAO_INICIADO;
        }

        try {
            return WhatsappSessionStatus.valueOf(
                    WhatsappGatewayStatusMapper.normalizar(resposta.status()));
        } catch (IllegalArgumentException ex) {
            return Boolean.TRUE.equals(resposta.conectado())
                    ? WhatsappSessionStatus.CONECTADO
                    : WhatsappSessionStatus.ERRO;
        }
    }

    private WhatsappSession novaSessao(Long idOrganizacao) {
        WhatsappSession sessao = new WhatsappSession();
        sessao.setIdOrganizacao(idOrganizacao);
        sessao.setTpStatus(WhatsappSessionStatus.NAO_INICIADO);
        sessao.setDsSessionPath("organizacao-" + idOrganizacao);
        return sessao;
    }
}
