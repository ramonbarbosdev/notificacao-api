package com.notificacao_api.service.queue;

import java.time.LocalDateTime;

import org.springframework.util.StringUtils;

import com.notificacao_api.enums.CanalNotificacao;
import com.notificacao_api.enums.CodigoErroEnvio;
import com.notificacao_api.enums.StatusNotificacao;
import com.notificacao_api.enums.StatusOperacionalSessao;
import com.notificacao_api.model.Notificacao;
import com.notificacao_api.model.WhatsappSession;

public final class AdminNotificacaoAcaoSugeridaResolver {

    private AdminNotificacaoAcaoSugeridaResolver() {
    }

    public record AcaoSugerida(
            String codigo,
            String titulo,
            String detalhe,
            boolean destaque) {
    }

    public static AcaoSugerida resolver(Notificacao notificacao, WhatsappSession sessaoWhatsapp, LocalDateTime agora) {
        if (notificacao == null) {
            return nenhuma();
        }

        StatusNotificacao status = notificacao.getStatus();
        String motivo = normalizar(notificacao.getMotivoAguardando());
        String erro = normalizar(notificacao.getErro());
        String codigoErro = notificacao.getCodigoErro();

        if (status == StatusNotificacao.FALHOU
                && CodigoErroEnvio.WHATSAPP_RESTRICAO_463.name().equals(codigoErro)) {
            return new AcaoSugerida(
                    "CONTATO_INICIAR_CONVERSA",
                    "Contato deve iniciar conversa",
                    "Peça ao destinatário enviar a 1ª mensagem. Depois use Reenviar — a sessão WhatsApp não precisa ser reativada.",
                    true);
        }

        if (notificacao.getCanal() == CanalNotificacao.WHATSAPP && sessaoPrecisaReativacao(sessaoWhatsapp, agora)) {
            if (status == StatusNotificacao.PENDENTE || status == StatusNotificacao.PROCESSANDO) {
                return new AcaoSugerida(
                        "REATIVAR_SESSAO_WHATSAPP",
                        "Reativar sessão WhatsApp",
                        "Envios da organização estão pausados ou em risco. Reative a operação antes de esperar a fila.",
                        true);
            }
        }

        if ((status == StatusNotificacao.PENDENTE || status == StatusNotificacao.PROCESSANDO)
                && contemAlgum(motivo, "pausada automaticamente", "risco operacional", "bloqueada por protecao")) {
            return new AcaoSugerida(
                    "REATIVAR_SESSAO_WHATSAPP",
                    "Reativar sessão WhatsApp",
                    "A fila está aguardando porque a sessão operacional não está ativa.",
                    true);
        }

        if (status == StatusNotificacao.PENDENTE || status == StatusNotificacao.PROCESSANDO) {
            if (StringUtils.hasText(motivo)) {
                return new AcaoSugerida(
                        "AGUARDAR_FILA",
                        "Aguardar retomada automática",
                        notificacao.getMotivoAguardando(),
                        false);
            }
            return new AcaoSugerida(
                    "CANCELAVEL",
                    "Pode cancelar ou aguardar",
                    "Envio ainda na fila. Cancele se não for mais necessário.",
                    false);
        }

        if (status == StatusNotificacao.FALHOU || status == StatusNotificacao.BLOQUEADA) {
            return new AcaoSugerida(
                    "REENVIAR",
                    "Reenviar ou cancelar",
                    StringUtils.hasText(erro) ? notificacao.getErro() : "Revise o erro e reenvie se fizer sentido.",
                    false);
        }

        if (status == StatusNotificacao.CANCELADA) {
            return new AcaoSugerida(
                    "REENVIAR",
                    "Reenviar se necessário",
                    "Notificação cancelada. Use Reenviar para colocá-la na fila novamente.",
                    false);
        }

        return nenhuma();
    }

    public static boolean sessaoPrecisaReativacao(WhatsappSession sessao, LocalDateTime agora) {
        if (sessao == null) {
            return false;
        }

        StatusOperacionalSessao status = sessao.getStatusOperacional();
        if (status == StatusOperacionalSessao.PAUSADA
                || status == StatusOperacionalSessao.RISCO_BANIMENTO
                || status == StatusOperacionalSessao.BLOQUEADA) {
            return true;
        }

        return sessao.getDtPausadoAte() != null && sessao.getDtPausadoAte().isAfter(agora);
    }

    public static boolean sessaoPodeCancelarPausa(WhatsappSession sessao) {
        if (sessao == null) {
            return false;
        }

        StatusOperacionalSessao status = sessao.getStatusOperacional();
        return status == StatusOperacionalSessao.PAUSADA
                || status == StatusOperacionalSessao.RISCO_BANIMENTO
                || status == StatusOperacionalSessao.BLOQUEADA;
    }

    private static AcaoSugerida nenhuma() {
        return new AcaoSugerida("NENHUMA", "—", null, false);
    }

    private static String normalizar(String texto) {
        return texto == null ? "" : texto.toLowerCase();
    }

    private static boolean contemAlgum(String texto, String... termos) {
        for (String termo : termos) {
            if (texto.contains(termo)) {
                return true;
            }
        }
        return false;
    }
}
