package com.notificacao_api.service.whatsapp;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.notificacao_api.dto.whatsapp.AcaoSessaoWhatsappDTO;
import com.notificacao_api.dto.whatsapp.SessaoOperacionalContextoDTO;
import com.notificacao_api.dto.whatsapp.StatusWhatsappResposta;
import com.notificacao_api.enums.StatusOperacionalSessao;
import com.notificacao_api.model.WhatsappSession;
import com.notificacao_api.repository.WhatsappSessionRepository;
import com.notificacao_api.service.queue.ProtecaoOperacionalConfigResolver;
import com.notificacao_api.service.queue.SegurancaOperacionalWhatsappService;

@Service
public class WhatsappSessaoOperacionalService {

    private static final DateTimeFormatter DATA_HORA_BR =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final WhatsappSessionRepository whatsappSessionRepository;
    private final ProtecaoOperacionalConfigResolver configResolver;
    private final SegurancaOperacionalWhatsappService segurancaService;

    public WhatsappSessaoOperacionalService(
            WhatsappSessionRepository whatsappSessionRepository,
            ProtecaoOperacionalConfigResolver configResolver,
            SegurancaOperacionalWhatsappService segurancaService) {
        this.whatsappSessionRepository = whatsappSessionRepository;
        this.configResolver = configResolver;
        this.segurancaService = segurancaService;
    }

    @Transactional
    public void reativarOperacao(Long idOrganizacao) {
        segurancaService.reativarSessao(idOrganizacao);
    }

    public StatusWhatsappResposta enriquecer(Long idOrganizacao, StatusWhatsappResposta resposta) {
        if (resposta == null) {
            return null;
        }
        SessaoOperacionalContextoDTO operacional = montarContexto(idOrganizacao, resposta);
        return resposta.comOperacional(operacional);
    }

    private SessaoOperacionalContextoDTO montarContexto(Long idOrganizacao, StatusWhatsappResposta resposta) {
        WhatsappSession sessao = whatsappSessionRepository.findByIdOrganizacao(idOrganizacao).orElse(null);
        StatusOperacionalSessao statusOperacional = sessao == null || sessao.getStatusOperacional() == null
                ? StatusOperacionalSessao.ATIVA
                : sessao.getStatusOperacional();
        int falhas = sessao == null || sessao.getFalhasConsecutivas() == null ? 0 : sessao.getFalhasConsecutivas();
        int maximoFalhas = configResolver.limiteFalhasSessao(idOrganizacao);
        LocalDateTime pausadoAte = sessao == null ? null : sessao.getDtPausadoAte();
        boolean conectado = Boolean.TRUE.equals(resposta.conectado());
        boolean pausaAtiva = pausadoAte != null && pausadoAte.isAfter(LocalDateTime.now());

        return switch (statusOperacional) {
            case RISCO_BANIMENTO -> contextoRisco(falhas, maximoFalhas, pausadoAte, conectado, pausaAtiva);
            case PAUSADA -> contextoPausada(falhas, maximoFalhas, pausadoAte, conectado, pausaAtiva);
            case BLOQUEADA -> contextoBloqueada(maximoFalhas, conectado);
            case DESCONECTADA -> contextoDesconectada(maximoFalhas, conectado);
            case ATIVA -> contextoAtiva(resposta, maximoFalhas, conectado);
        };
    }

    private SessaoOperacionalContextoDTO contextoAtiva(
            StatusWhatsappResposta resposta,
            int maximoFalhas,
            boolean conectado) {
        if (!conectado) {
            return new SessaoOperacionalContextoDTO(
                    StatusOperacionalSessao.ATIVA.name(),
                    0,
                    maximoFalhas,
                    null,
                    "WhatsApp desconectado",
                    "A sessao operacional esta liberada, mas o numero nao esta conectado ao gateway.",
                    "Conecte o WhatsApp e escaneie o QR Code para voltar a enviar mensagens.",
                    List.of(
                            acao("CONECTAR", "Conectar WhatsApp", "Inicia uma nova conexao e exibe o QR Code.", true, true),
                            acao("ATUALIZAR_STATUS", "Atualizar status", "Consulta novamente o gateway.", false, true)));
        }

        if (resposta.erro() != null && !resposta.erro().isBlank()) {
            return new SessaoOperacionalContextoDTO(
                    StatusOperacionalSessao.ATIVA.name(),
                    0,
                    maximoFalhas,
                    null,
                    "Conexao com aviso",
                    "O WhatsApp esta conectado, mas o gateway reportou um aviso: " + resposta.erro(),
                    "Se os envios falharem repetidamente, a sessao pode ser pausada automaticamente.",
                    List.of(
                            acao("ATUALIZAR_STATUS", "Atualizar status", "Verifica o estado atual no gateway.", true, true),
                            acao("DESCONECTAR", "Desconectar", "Encerra a sessao, remove tokens e arquivos locais. Exige novo QR Code.", false, true)));
        }

        return new SessaoOperacionalContextoDTO(
                StatusOperacionalSessao.ATIVA.name(),
                0,
                maximoFalhas,
                null,
                "Operacao normal",
                "A sessao esta ativa e os envios pela fila podem prosseguir dentro dos limites configurados.",
                "Mensagens na fila serao processadas automaticamente.",
                List.of(
                        acao("ATUALIZAR_STATUS", "Atualizar status", "Consulta o gateway.", false, true),
                        acao("DESCONECTAR", "Desconectar", "Encerra a sessao, remove tokens e arquivos locais. Exige novo QR Code.", false, true)));
    }

    private SessaoOperacionalContextoDTO contextoPausada(
            int falhas,
            int maximoFalhas,
            LocalDateTime pausadoAte,
            boolean conectado,
            boolean pausaAtiva) {
        String ate = formatarData(pausadoAte);
        List<AcaoSessaoWhatsappDTO> acoes = new ArrayList<>();
        acoes.add(acao(
                "AGUARDAR_PAUSA",
                pausaAtiva ? "Aguardar fim da pausa" : "Pausa encerrada",
                pausaAtiva
                        ? "A fila retomara tentativas apos " + ate + "."
                        : "A pausa automatica ja terminou. Aguarde a retomada ou contate o suporte.",
                true,
                false));
        if (!conectado) {
            acoes.add(acao("CONECTAR", "Conectar WhatsApp", "Reconecte o numero antes de retomar os envios.", false, true));
        }
        acoes.add(acao("ATUALIZAR_STATUS", "Atualizar status", "Consulta o gateway novamente.", false, true));

        return new SessaoOperacionalContextoDTO(
                StatusOperacionalSessao.PAUSADA.name(),
                falhas,
                maximoFalhas,
                ate,
                "Envios pausados automaticamente",
                "Houve falhas consecutivas ao enviar mensagens (" + falhas + " de " + maximoFalhas
                        + "). A protecao pausou novos envios para evitar bloqueio pelo WhatsApp.",
                pausaAtiva
                        ? "Aguarde ate " + ate + " ou corrija a causa (gateway offline, numero invalido, etc.). "
                                + "Somente o suporte pode cancelar a pausa antes do prazo."
                        : "A pausa automatica terminou. Se os envios nao retomarem, contate o suporte.",
                acoes);
    }

    private SessaoOperacionalContextoDTO contextoRisco(
            int falhas,
            int maximoFalhas,
            LocalDateTime pausadoAte,
            boolean conectado,
            boolean pausaAtiva) {
        List<AcaoSessaoWhatsappDTO> acoes = new ArrayList<>();
        if (!conectado) {
            acoes.add(acao("CONECTAR", "Conectar WhatsApp", "Reconecte o numero e escaneie o QR Code.", true, true));
        }
        acoes.add(acao("ATUALIZAR_STATUS", "Atualizar status", "Consulta o estado no gateway.", false, true));

        return new SessaoOperacionalContextoDTO(
                StatusOperacionalSessao.RISCO_BANIMENTO.name(),
                falhas,
                maximoFalhas,
                formatarData(pausadoAte),
                "Sessao em risco operacional",
                "Atingiu " + falhas + " falhas consecutivas (limite: " + maximoFalhas
                        + "). Novos envios WhatsApp estao bloqueados para proteger o numero contra banimento.",
                pausaAtiva
                        ? "Corrija o problema e contate o suporte para liberar a operacao."
                        : "Corrija o problema e contate o suporte para liberar a operacao.",
                acoes);
    }

    private SessaoOperacionalContextoDTO contextoBloqueada(int maximoFalhas, boolean conectado) {
        List<AcaoSessaoWhatsappDTO> acoes = new ArrayList<>();
        if (!conectado) {
            acoes.add(acao("CONECTAR", "Conectar WhatsApp", "Reconecte o numero.", false, true));
        }
        acoes.add(acao("ATUALIZAR_STATUS", "Atualizar status", "Consulta o gateway.", false, true));

        return new SessaoOperacionalContextoDTO(
                StatusOperacionalSessao.BLOQUEADA.name(),
                0,
                maximoFalhas,
                null,
                "Sessao bloqueada",
                "A sessao foi bloqueada apos falhas consecutivas repetidas. Nenhum envio WhatsApp sera processado ate liberacao pelo suporte.",
                "Contate o suporte para cancelar a pausa e liberar os envios.",
                acoes);
    }

    private SessaoOperacionalContextoDTO contextoDesconectada(int maximoFalhas, boolean conectado) {
        return new SessaoOperacionalContextoDTO(
                StatusOperacionalSessao.DESCONECTADA.name(),
                0,
                maximoFalhas,
                null,
                "Sessao desconectada",
                "A sessao operacional esta marcada como desconectada.",
                conectado
                        ? "O gateway indica conexao, mas o status operacional ainda e desconectado. Atualize o status."
                        : "Conecte o WhatsApp para retomar os envios.",
                List.of(
                        acao("CONECTAR", "Conectar WhatsApp", "Inicia conexao com QR Code.", true, true),
                        acao("ATUALIZAR_STATUS", "Atualizar status", "Consulta o gateway.", false, true)));
    }

    private AcaoSessaoWhatsappDTO acao(
            String codigo,
            String rotulo,
            String descricao,
            boolean primaria,
            boolean habilitada) {
        return new AcaoSessaoWhatsappDTO(codigo, rotulo, descricao, primaria, habilitada);
    }

    private String formatarData(LocalDateTime valor) {
        return valor == null ? null : valor.format(DATA_HORA_BR);
    }
}
