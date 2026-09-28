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
import com.notificacao_api.repository.NotificacaoRepository;
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
    private final NotificacaoService notificacaoService;
    private final NotificacaoRepository notificacaoRepository;
    private final ProtecaoNotificacaoService protecaoNotificacaoService;
    private final AuditoriaEventoService auditoriaEventoService;
    private final int confirmacaoIntervaloDias;

    public TcTokenAudienciaReativacaoService(
            TenantContextService tenantContextService,
            TcTokenAudienciaMonitorService monitorService,
            OrganizacaoConfiguracaoService organizacaoConfiguracaoService,
            NotificacaoService notificacaoService,
            NotificacaoRepository notificacaoRepository,
            ProtecaoNotificacaoService protecaoNotificacaoService,
            AuditoriaEventoService auditoriaEventoService,
            @Value("${notificacao.tctoken.confirmacao-intervalo-dias:7}") int confirmacaoIntervaloDias) {
        this.tenantContextService = tenantContextService;
        this.monitorService = monitorService;
        this.organizacaoConfiguracaoService = organizacaoConfiguracaoService;
        this.notificacaoService = notificacaoService;
        this.notificacaoRepository = notificacaoRepository;
        this.protecaoNotificacaoService = protecaoNotificacaoService;
        this.auditoriaEventoService = auditoriaEventoService;
        this.confirmacaoIntervaloDias = Math.max(1, confirmacaoIntervaloDias);
    }

    public EnviarNotificacaoResposta pedirConfirmacao(TcTokenAudienciaPedirConfirmacaoRequest request) {
        Long idOrganizacao = tenantContextService.idOrganizacaoObrigatoria();
        String telefone = normalizarTelefone(request.telefone());
        boolean modoTeste = Boolean.TRUE.equals(request.modoTeste());

        if (modoTeste) {
            if (!monitorService.telefoneNaAudiencia(idOrganizacao, telefone)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Telefone fora da audiencia monitorada (GitHub ou fila recente).");
            }
        } else if (!monitorService.telefoneNaAudienciaEmUso(idOrganizacao, telefone)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Telefone fora da audiencia em uso (GitHub ativo ou envio recente).");
        }

        Optional<TcTokenAudienciaLinhaResponse> linhaCache = monitorService.buscarLinhaNoCache(idOrganizacao, telefone);
        TcTokenAudienciaLinhaResponse linha;

        if (modoTeste) {
            linha = linhaCache.orElse(linhaPlaceholderTeste(telefone));
        } else {
            if (linhaCache.isEmpty()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Atualize a monitoracao antes de pedir confirmacao (varredura gateway).");
            }
            linha = linhaCache.get();
            if (!linha.consultadoNoGateway()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Numero fora do escopo de consulta gateway; nao e elegivel para pedido de confirmacao.");
            }
            if (!situacaoElegivel(linha.situacao())) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Situação do token nao exige pedido de confirmacao neste momento.");
            }
        }

        organizacaoConfiguracaoService.validarEnvioMensagensHabilitado(idOrganizacao);

        String referencia = referenciaConfirmacao(idOrganizacao, telefone, modoTeste);
        if (!modoTeste) {
            validarDedupeConfirmacao(idOrganizacao, telefone, referencia);
        }

        String mensagem = montarMensagem(request.mensagem(), linha, modoTeste);

        EnviarNotificacaoRequisicao enfileirar = new EnviarNotificacaoRequisicao(
                CanalNotificacao.WHATSAPP,
                telefone,
                null,
                mensagem,
                null,
                MARCADOR_VARIAVEIS_CONFIRMACAO + "|" + referencia,
                referencia);

        EnviarNotificacaoResposta resposta = notificacaoService.enviar(enfileirar);

        if (!modoTeste
                && resposta.status() == StatusNotificacao.BLOQUEADA
                && resposta.erro() != null
                && resposta.erro().contains("duplicada")) {
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Ja existe pedido de confirmacao recente para este numero.");
        }

        auditoriaEventoService.registrar(
                idOrganizacao,
                "WHATSAPP",
                modoTeste ? "TCTOKEN_PEDIR_CONFIRMACAO_TESTE" : "TCTOKEN_PEDIR_CONFIRMACAO",
                modoTeste
                        ? "Pedido de confirmacao WhatsApp enfileirado em modo teste (admin)."
                        : "Pedido de confirmacao de notificacoes WhatsApp enfileirado.",
                null,
                java.util.Map.of(
                        "telefone", telefone,
                        "referenciaExterna", referencia,
                        "modoTeste", modoTeste,
                        "idNotificacao", resposta.idNotificacao(),
                        "status", resposta.status()));

        return resposta;
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

    private static boolean situacaoElegivel(TcTokenAudienciaSituacao situacao) {
        return situacao == TcTokenAudienciaSituacao.PROXIMO_EXPIRAR
                || situacao == TcTokenAudienciaSituacao.EXPIRADO
                || situacao == TcTokenAudienciaSituacao.AUSENTE;
    }

    static String montarMensagem(String mensagemInformada, TcTokenAudienciaLinhaResponse linha) {
        return montarMensagem(mensagemInformada, linha, false);
    }

    static String montarMensagem(String mensagemInformada, TcTokenAudienciaLinhaResponse linha, boolean modoTeste) {
        String corpo;
        if (StringUtils.hasText(mensagemInformada)) {
            corpo = mensagemInformada.trim();
        } else {
            String saudacao = "Ola!";
            if (linha.origens().contains(TcTokenAudienciaOrigem.GITHUB)
                    && linha.nomeExibicao() != null
                    && linha.nomeExibicao().startsWith("@")) {
                saudacao = "Ola " + linha.nomeExibicao() + "!";
            }

            StringBuilder builder = new StringBuilder();
            builder.append(saudacao)
                    .append(" Para continuar recebendo avisos por WhatsApp, precisamos manter a conversa ativa.");

            if (linha.expiraEmDias() != null && linha.expiraEmDias() > 0) {
                builder.append(" Seu token de conversa expira em cerca de ")
                        .append(Math.round(linha.expiraEmDias()))
                        .append(" dia(s).");
            }

            builder.append(" Responda esta mensagem confirmando que ainda deseja receber notificacoes.");
            corpo = builder.toString();
        }

        if (modoTeste) {
            return "[TESTE ADMIN] " + corpo;
        }
        return corpo;
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
