package com.notificacao_api.service.whatsapp;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import com.notificacao_api.dto.notificacao.EnviarNotificacaoRequisicao;
import com.notificacao_api.dto.notificacao.EnviarNotificacaoResposta;
import com.notificacao_api.dto.whatsapp.TcTokenAudienciaLinhaResponse;
import com.notificacao_api.dto.whatsapp.TcTokenAudienciaPedirConfirmacaoRequest;
import com.notificacao_api.enums.CanalNotificacao;
import com.notificacao_api.enums.StatusNotificacao;
import com.notificacao_api.enums.TcTokenAudienciaOrigem;
import com.notificacao_api.enums.TcTokenAudienciaSituacao;
import com.notificacao_api.model.OrganizacaoGithubResponsavel;
import com.notificacao_api.repository.NotificacaoRepository;
import com.notificacao_api.repository.OrganizacaoGithubResponsavelRepository;
import com.notificacao_api.service.AuditoriaEventoService;
import com.notificacao_api.service.NotificacaoService;
import com.notificacao_api.service.OrganizacaoConfiguracaoService;
import com.notificacao_api.service.TenantContextService;
import com.notificacao_api.service.queue.ProtecaoNotificacaoService;
import com.notificacao_api.shared.TelefoneBrasilUtil;

@Service
public class TcTokenAudienciaReativacaoService {

    static final String MARCADOR_VARIAVEIS_CONFIRMACAO = "TCTOKEN_PEDIR_CONFIRMACAO";

    private static final List<StatusNotificacao> STATUS_DEDUPE_CONFIRMACAO = List.of(
            StatusNotificacao.PENDENTE,
            StatusNotificacao.PROCESSANDO,
            StatusNotificacao.ENVIADA,
            StatusNotificacao.ENTREGUE,
            StatusNotificacao.LIDA);

    private final TenantContextService tenantContextService;
    private final TcTokenAudienciaMonitorService monitorService;
    private final OrganizacaoConfiguracaoService organizacaoConfiguracaoService;
    private final OrganizacaoGithubResponsavelRepository githubResponsavelRepository;
    private final NotificacaoService notificacaoService;
    private final NotificacaoRepository notificacaoRepository;
    private final ProtecaoNotificacaoService protecaoNotificacaoService;
    private final AuditoriaEventoService auditoriaEventoService;
    private final int confirmacaoIntervaloDias;

    public TcTokenAudienciaReativacaoService(
            TenantContextService tenantContextService,
            TcTokenAudienciaMonitorService monitorService,
            OrganizacaoConfiguracaoService organizacaoConfiguracaoService,
            OrganizacaoGithubResponsavelRepository githubResponsavelRepository,
            NotificacaoService notificacaoService,
            NotificacaoRepository notificacaoRepository,
            ProtecaoNotificacaoService protecaoNotificacaoService,
            AuditoriaEventoService auditoriaEventoService,
            @Value("${notificacao.tctoken.confirmacao-intervalo-dias:7}") int confirmacaoIntervaloDias) {
        this.tenantContextService = tenantContextService;
        this.monitorService = monitorService;
        this.organizacaoConfiguracaoService = organizacaoConfiguracaoService;
        this.githubResponsavelRepository = githubResponsavelRepository;
        this.notificacaoService = notificacaoService;
        this.notificacaoRepository = notificacaoRepository;
        this.protecaoNotificacaoService = protecaoNotificacaoService;
        this.auditoriaEventoService = auditoriaEventoService;
        this.confirmacaoIntervaloDias = Math.max(1, confirmacaoIntervaloDias);
    }

    public EnviarNotificacaoResposta pedirConfirmacao(TcTokenAudienciaPedirConfirmacaoRequest request) {
        Long idOrganizacao = tenantContextService.idOrganizacaoObrigatoria();
        return pedirConfirmacaoOrganizacao(idOrganizacao, request, TcTokenPedidoConfirmacaoModo.MANUAL, null);
    }

    public EnviarNotificacaoResposta pedirConfirmacaoAutomatica(
            Long idOrganizacao,
            TcTokenAudienciaLinhaResponse linha) {
        if (linha == null || !StringUtils.hasText(linha.telefone())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Linha invalida para confirmacao automatica.");
        }
        TcTokenAudienciaPedirConfirmacaoRequest request =
                new TcTokenAudienciaPedirConfirmacaoRequest(linha.telefone(), null, null);
        return pedirConfirmacaoOrganizacao(idOrganizacao, request, TcTokenPedidoConfirmacaoModo.AUTOMATICO, linha);
    }

    public boolean linhaElegivelConfirmacaoAutomatica(
            Long idOrganizacao,
            TcTokenAudienciaLinhaResponse linha,
            int diasAntesExpirar) {
        if (linha == null || !linha.consultadoNoGateway()) {
            return false;
        }
        if (linha.origens() == null || !linha.origens().contains(TcTokenAudienciaOrigem.GITHUB)) {
            return false;
        }
        if (!githubOptInAtivo(idOrganizacao, linha.telefone())) {
            return false;
        }
        Double expiraEmDias = linha.expiraEmDias();
        return expiraEmDias != null && expiraEmDias > 0 && expiraEmDias <= diasAntesExpirar;
    }

    EnviarNotificacaoResposta pedirConfirmacaoOrganizacao(
            Long idOrganizacao,
            TcTokenAudienciaPedirConfirmacaoRequest request,
            TcTokenPedidoConfirmacaoModo modo,
            TcTokenAudienciaLinhaResponse linhaAutomatica) {
        String telefone = normalizarTelefone(request.telefone());
        boolean modoTeste = Boolean.TRUE.equals(request.modoTeste()) && modo == TcTokenPedidoConfirmacaoModo.MANUAL;

        TcTokenAudienciaLinhaResponse linha = resolverLinha(idOrganizacao, telefone, modo, modoTeste, linhaAutomatica);

        if (modo == TcTokenPedidoConfirmacaoModo.AUTOMATICO) {
            int diasAntes = organizacaoConfiguracaoService.tctokenConfirmacaoAutomaticaDiasAntes(idOrganizacao);
            if (!linhaElegivelConfirmacaoAutomatica(idOrganizacao, linha, diasAntes)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Linha nao elegivel para confirmacao automatica.");
            }
        }

        organizacaoConfiguracaoService.validarEnvioMensagensHabilitado(idOrganizacao);

        String referencia = referenciaConfirmacao(idOrganizacao, telefone, modoTeste);
        if (!modoTeste) {
            validarDedupeConfirmacao(idOrganizacao, telefone, referencia);
        }

        String template = organizacaoConfiguracaoService.tctokenConfirmacaoMensagemPadrao(idOrganizacao);
        String mensagem = TcTokenConfirmacaoMensagemSupport.resolverMensagem(
                request.mensagem(), template, linha, modoTeste);

        EnviarNotificacaoRequisicao enfileirar = new EnviarNotificacaoRequisicao(
                CanalNotificacao.WHATSAPP,
                telefone,
                null,
                mensagem,
                null,
                MARCADOR_VARIAVEIS_CONFIRMACAO + "|" + referencia,
                referencia);

        EnviarNotificacaoResposta resposta = modo == TcTokenPedidoConfirmacaoModo.MANUAL
                ? notificacaoService.enviar(enfileirar)
                : notificacaoService.enviarParaOrganizacao(idOrganizacao, enfileirar);

        if (!modoTeste
                && resposta.status() == StatusNotificacao.BLOQUEADA
                && resposta.erro() != null
                && resposta.erro().contains("duplicada")) {
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Ja existe pedido de confirmacao recente para este numero.");
        }

        String acaoAuditoria = switch (modo) {
            case AUTOMATICO -> "TCTOKEN_PEDIR_CONFIRMACAO_AUTOMATICO";
            case MANUAL -> modoTeste ? "TCTOKEN_PEDIR_CONFIRMACAO_TESTE" : "TCTOKEN_PEDIR_CONFIRMACAO";
        };
        String descricaoAuditoria = switch (modo) {
            case AUTOMATICO -> "Pedido de confirmacao tctoken enfileirado pelo motor automatico.";
            case MANUAL -> modoTeste
                    ? "Pedido de confirmacao WhatsApp enfileirado em modo teste (admin)."
                    : "Pedido de confirmacao de notificacoes WhatsApp enfileirado.";
        };

        auditoriaEventoService.registrar(
                idOrganizacao,
                "WHATSAPP",
                acaoAuditoria,
                descricaoAuditoria,
                null,
                java.util.Map.of(
                        "telefone", telefone,
                        "referenciaExterna", referencia,
                        "modo", modo.name(),
                        "modoTeste", modoTeste,
                        "idNotificacao", resposta.idNotificacao(),
                        "status", resposta.status()));

        return resposta;
    }

    private TcTokenAudienciaLinhaResponse resolverLinha(
            Long idOrganizacao,
            String telefone,
            TcTokenPedidoConfirmacaoModo modo,
            boolean modoTeste,
            TcTokenAudienciaLinhaResponse linhaAutomatica) {
        if (modo == TcTokenPedidoConfirmacaoModo.AUTOMATICO) {
            if (linhaAutomatica == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Linha obrigatoria para modo automatico.");
            }
            return linhaAutomatica;
        }

        if (modoTeste) {
            if (!monitorService.telefoneNaAudiencia(idOrganizacao, telefone)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Telefone fora da audiencia monitorada (GitHub ou fila recente).");
            }
            return monitorService.buscarLinhaNoCache(idOrganizacao, telefone).orElse(linhaPlaceholderTeste(telefone));
        }

        if (!monitorService.telefoneNaAudienciaEmUso(idOrganizacao, telefone)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Telefone fora da audiencia em uso (GitHub ativo ou envio recente).");
        }

        Optional<TcTokenAudienciaLinhaResponse> linhaCache = monitorService.buscarLinhaNoCache(idOrganizacao, telefone);
        if (linhaCache.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Atualize a monitoracao antes de pedir confirmacao (varredura gateway).");
        }
        TcTokenAudienciaLinhaResponse linha = linhaCache.get();
        if (!linha.consultadoNoGateway()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Numero fora do escopo de consulta gateway; nao e elegivel para pedido de confirmacao.");
        }
        if (!situacaoElegivelManual(linha.situacao())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Situação do token nao exige pedido de confirmacao neste momento.");
        }
        return linha;
    }

    boolean githubOptInAtivo(Long idOrganizacao, String telefone) {
        Optional<OrganizacaoGithubResponsavel> responsavel =
                githubResponsavelRepository.findByIdOrganizacaoAndNuWhatsapp(idOrganizacao, telefone);
        return responsavel
                .filter(r -> Boolean.TRUE.equals(r.getAtivo()))
                .filter(r -> StringUtils.hasText(r.getDsGithubLogin()))
                .isPresent();
    }

    static String referenciaConfirmacao(Long idOrganizacao, String telefone) {
        return referenciaConfirmacao(idOrganizacao, telefone, false);
    }

    static String referenciaConfirmacao(Long idOrganizacao, String telefone, boolean modoTeste) {
        if (modoTeste) {
            return "tctoken-cfm-teste:" + idOrganizacao + ":" + telefone + ":" + System.currentTimeMillis();
        }
        return "tctoken-cfm:" + idOrganizacao + ":" + telefone;
    }

    private static TcTokenAudienciaLinhaResponse linhaPlaceholderTeste(String telefone) {
        return new TcTokenAudienciaLinhaResponse(
                telefone,
                telefone,
                "—",
                List.of(),
                null,
                null,
                TcTokenAudienciaSituacao.OK,
                "Teste",
                null,
                true,
                null,
                false);
    }

    private void validarDedupeConfirmacao(Long idOrganizacao, String telefone, String referencia) {
        LocalDateTime desde = protecaoNotificacaoService.agora().minusDays(confirmacaoIntervaloDias);
        String marcador = MARCADOR_VARIAVEIS_CONFIRMACAO + "|" + referencia;
        boolean recente = notificacaoRepository.existsByIdOrganizacaoAndCanalAndDestinatarioAndVariaveisTemplateAndDtCriacaoAfterAndStatusIn(
                idOrganizacao,
                CanalNotificacao.WHATSAPP,
                telefone,
                marcador,
                desde,
                STATUS_DEDUPE_CONFIRMACAO);
        if (recente) {
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Ja foi enviado pedido de confirmacao para este numero nos ultimos "
                            + confirmacaoIntervaloDias
                            + " dias.");
        }
    }

    private static boolean situacaoElegivelManual(TcTokenAudienciaSituacao situacao) {
        return situacao == TcTokenAudienciaSituacao.PROXIMO_EXPIRAR
                || situacao == TcTokenAudienciaSituacao.EXPIRADO
                || situacao == TcTokenAudienciaSituacao.AUSENTE;
    }

    static String montarMensagem(String mensagemInformada, TcTokenAudienciaLinhaResponse linha) {
        return montarMensagem(mensagemInformada, linha, false);
    }

    static String montarMensagem(String mensagemInformada, TcTokenAudienciaLinhaResponse linha, boolean modoTeste) {
        return TcTokenConfirmacaoMensagemSupport.resolverMensagem(mensagemInformada, null, linha, modoTeste);
    }

    static String montarMensagemSistema(TcTokenAudienciaLinhaResponse linha) {
        String saudacao = "Ola!";
        if (linha != null
                && linha.origens() != null
                && linha.origens().contains(TcTokenAudienciaOrigem.GITHUB)
                && linha.nomeExibicao() != null
                && linha.nomeExibicao().startsWith("@")) {
            saudacao = "Ola " + linha.nomeExibicao() + "!";
        }
        StringBuilder corpo = new StringBuilder(saudacao)
                .append(" Para continuar recebendo avisos por WhatsApp, precisamos manter a conversa ativa.");
        if (linha != null && linha.expiraEmDias() != null && linha.expiraEmDias() > 0) {
            corpo.append(" Seu token de conversa expira em cerca de ")
                    .append(Math.round(linha.expiraEmDias()))
                    .append(" dia(s).");
        }
        corpo.append(" Responda esta mensagem confirmando que ainda deseja receber notificacoes.");
        return corpo.toString();
    }

    private static String normalizarTelefone(String bruto) {
        if (!StringUtils.hasText(bruto)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Telefone obrigatorio.");
        }
        try {
            String normalizado = TelefoneBrasilUtil.normalizarDestino(CanalNotificacao.WHATSAPP, bruto);
            if (!TelefoneBrasilUtil.celularBrasilComNonoDigito(normalizado)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Telefone WhatsApp invalido.");
            }
            return normalizado;
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Telefone WhatsApp invalido.");
        }
    }
}
