package com.notificacao_api.service.whatsapp;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.notificacao_api.config.PropriedadesProtecaoNotificacao;
import com.notificacao_api.dto.whatsapp.AcaoSessaoWhatsappDTO;
import com.notificacao_api.dto.whatsapp.SessaoOperacionalContextoDTO;
import com.notificacao_api.dto.whatsapp.StatusWhatsappResposta;
import com.notificacao_api.enums.StatusOperacionalSessao;
import com.notificacao_api.model.WhatsappSession;
import com.notificacao_api.repository.WhatsappSessionRepository;
import com.notificacao_api.service.queue.SegurancaOperacionalWhatsappService;

@Service
public class WhatsappSessaoOperacionalService {

    private static final DateTimeFormatter DATA_HORA_BR =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final WhatsappSessionRepository whatsappSessionRepository;
    private final PropriedadesProtecaoNotificacao propriedades;
    private final SegurancaOperacionalWhatsappService segurancaService;

    public WhatsappSessaoOperacionalService(
            WhatsappSessionRepository whatsappSessionRepository,
            PropriedadesProtecaoNotificacao propriedades,
            SegurancaOperacionalWhatsappService segurancaService) {
        this.whatsappSessionRepository = whatsappSessionRepository;
        this.propriedades = propriedades;
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
        int maximoFalhas = propriedades.maximoFalhasConsecutivas();
        LocalDateTime pausadoAte = sessao == null ? null : sessao.getDtPausadoAte();
        boolean conectado = Boolean.TRUE.equals(resposta.conectado());
        boolean pausaAtiva = pausadoAte != null && pausadoAte.isAfter(LocalDateTime.now());

        return switch (statusOperacional) {
            case RISCO_BANIMENTO -> contextoRisco(falhas, maximoFalhas, pausadoAte, conectado, pausaAtiva);
            case PAUSADA -> contextoPausada(falhas, maximoFalhas, pausadoAte, conectado, pausaAtiva);
            case BLOQUEADA -> contextoBloqueada(conectado);
            case DESCONECTADA -> contextoDesconectada(conectado);
            case ATIVA -> contextoAtiva(resposta, conectado);
        };
    }

    private SessaoOperacionalContextoDTO contextoAtiva(StatusWhatsappResposta resposta, boolean conectado) {
        if (!conectado) {
            return new SessaoOperacionalContextoDTO(
                    StatusOperacionalSessao.ATIVA.name(),
                    0,
                    propriedades.maximoFalhasConsecutivas(),
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
                    propriedades.maximoFalhasConsecutivas(),
                    null,
                    "Conexao com aviso",
                    "O WhatsApp esta conectado, mas o gateway reportou um aviso: " + resposta.erro(),
                    "Se os envios falharem repetidamente, a sessao pode ser pausada automaticamente.",
                    List.of(
                            acao("ATUALIZAR_STATUS", "Atualizar status", "Verifica o estado atual no gateway.", true, true),
                            acao("DESCONECTAR", "Desconectar", "Encerra a sessao atual.", false, true)));
        }

        return new SessaoOperacionalContextoDTO(
                StatusOperacionalSessao.ATIVA.name(),
                0,
                propriedades.maximoFalhasConsecutivas(),
                null,
                "Operacao normal",
                "A sessao esta ativa e os envios pela fila podem prosseguir dentro dos limites configurados.",
                "Mensagens na fila serao processadas automaticamente.",
                List.of(
                        acao("ATUALIZAR_STATUS", "Atualizar status", "Consulta o gateway.", false, true),
                        acao("DESCONECTAR", "Desconectar", "Encerra a sessao WhatsApp.", false, true)));
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
                        : "A pausa automatica ja terminou. Voce pode reativar a operacao.",
                true,
                !pausaAtiva));
        if (!pausaAtiva) {
            acoes.add(acao(
                    "REATIVAR_OPERACAO",
                    "Reativar envios",
                    "Libera a fila apos voce confirmar que o problema foi resolvido.",
                    true,
                    true));
        }
        if (!conectado) {
            acoes.add(acao("CONECTAR", "Conectar WhatsApp", "Reconecte o numero antes de reativar os envios.", false, true));
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
                        ? "Aguarde ate " + ate + " ou corrija a causa (gateway offline, numero invalido, etc.) e reative manualmente."
                        : "A pausa automatica terminou. Se o problema foi corrigido, clique em Reativar envios.",
                acoes);
    }

    private SessaoOperacionalContextoDTO contextoRisco(
            int falhas,
            int maximoFalhas,
            LocalDateTime pausadoAte,
            boolean conectado,
            boolean pausaAtiva) {
        List<AcaoSessaoWhatsappDTO> acoes = new ArrayList<>();
        acoes.add(acao(
                "REATIVAR_OPERACAO",
                "Reativar sessao",
                "Use apos corrigir a causa das falhas (gateway, conexao, numero).",
                true,
                true));
        if (!conectado) {
            acoes.add(acao("CONECTAR", "Conectar WhatsApp", "Reconecte o numero e escaneie o QR Code.", false, true));
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
                        ? "Corrija o problema, reconecte se necessario e clique em Reativar sessao. Mensagens PENDENTES na fila aguardam liberacao."
                        : "Corrija o problema e clique em Reativar sessao. As mensagens na fila continuam PENDENTES ate a liberacao.",
                acoes);
    }

    private SessaoOperacionalContextoDTO contextoBloqueada(boolean conectado) {
        List<AcaoSessaoWhatsappDTO> acoes = new ArrayList<>();
        acoes.add(acao(
                "REATIVAR_OPERACAO",
                "Solicitar reativacao",
                "Tenta liberar a sessao apos revisao manual.",
                true,
                true));
        if (!conectado) {
            acoes.add(acao("CONECTAR", "Conectar WhatsApp", "Reconecte o numero.", false, true));
        }
        acoes.add(acao("ATUALIZAR_STATUS", "Atualizar status", "Consulta o gateway.", false, true));

        return new SessaoOperacionalContextoDTO(
                StatusOperacionalSessao.BLOQUEADA.name(),
                0,
                propriedades.maximoFalhasConsecutivas(),
                null,
                "Sessao bloqueada",
                "A sessao foi bloqueada por protecao operacional. Nenhum envio WhatsApp sera processado.",
                "Revise alertas operacionais e contate o suporte se necessario antes de reativar.",
                acoes);
    }

    private SessaoOperacionalContextoDTO contextoDesconectada(boolean conectado) {
        return new SessaoOperacionalContextoDTO(
                StatusOperacionalSessao.DESCONECTADA.name(),
                0,
                propriedades.maximoFalhasConsecutivas(),
                null,
                "Sessao desconectada",
                "A sessao operacional esta marcada como desconectada.",
                conectado
                        ? "O gateway indica conexao, mas o status operacional ainda e desconectado. Atualize ou reative."
                        : "Conecte o WhatsApp para retomar os envios.",
                List.of(
                        acao("CONECTAR", "Conectar WhatsApp", "Inicia conexao com QR Code.", true, true),
                        acao("REATIVAR_OPERACAO", "Reativar operacao", "Marca a sessao como ativa apos reconectar.", false, true),
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
