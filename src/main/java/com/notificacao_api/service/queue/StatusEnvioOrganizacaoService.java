package com.notificacao_api.service.queue;

import java.time.Duration;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.notificacao_api.dto.notificacao.StatusEnvioOrganizacaoResponse;
import com.notificacao_api.dto.whatsapp.SessaoOperacionalContextoDTO;
import com.notificacao_api.dto.whatsapp.StatusWhatsappResposta;
import com.notificacao_api.enums.CanalNotificacao;
import com.notificacao_api.enums.StatusOperacionalSessao;
import com.notificacao_api.model.Notificacao;
import com.notificacao_api.model.WhatsappSession;
import com.notificacao_api.repository.WhatsappSessionRepository;
import com.notificacao_api.service.TenantContextService;
import com.notificacao_api.service.whatsapp.WhatsappSessaoService;

@Service
public class StatusEnvioOrganizacaoService {

    private final TenantContextService tenantContextService;
    private final ProtecaoNotificacaoService protecaoService;
    private final WhatsappSessaoService whatsappSessaoService;
    private final WhatsappSessionRepository whatsappSessionRepository;

    public StatusEnvioOrganizacaoService(
            TenantContextService tenantContextService,
            ProtecaoNotificacaoService protecaoService,
            WhatsappSessaoService whatsappSessaoService,
            WhatsappSessionRepository whatsappSessionRepository) {
        this.tenantContextService = tenantContextService;
        this.protecaoService = protecaoService;
        this.whatsappSessaoService = whatsappSessaoService;
        this.whatsappSessionRepository = whatsappSessionRepository;
    }

    @Transactional(readOnly = true)
    public StatusEnvioOrganizacaoResponse consultar(CanalNotificacao canal) {
        CanalNotificacao canalConsulta = canal != null ? canal : CanalNotificacao.WHATSAPP;
        Long idOrganizacao = tenantContextService.idOrganizacaoObrigatoria();

        Notificacao probe = new Notificacao();
        probe.setIdOrganizacao(idOrganizacao);
        probe.setCanal(canalConsulta);

        DecisaoProtecaoNotificacao decisao = protecaoService.avaliar(probe);
        SessaoOperacionalContextoDTO operacional = null;

        if (canalConsulta == CanalNotificacao.WHATSAPP) {
            try {
                StatusWhatsappResposta status = whatsappSessaoService.obterStatus();
                operacional = status == null ? null : status.operacional();
            } catch (Exception ex) {
                operacional = null;
            }
        }

        if (decisao.permitida()) {
            if (operacional != null && !sessaoOperacionalAtiva(operacional, idOrganizacao)) {
                return montarBloqueioOperacional(canalConsulta, operacional);
            }
            return StatusEnvioOrganizacaoResponse.liberado(canalConsulta);
        }

        return montarBloqueioProtecao(canalConsulta, decisao, operacional);
    }

    private boolean sessaoOperacionalAtiva(SessaoOperacionalContextoDTO operacional, Long idOrganizacao) {
        if ("ATIVA".equals(operacional.statusOperacional())) {
            return true;
        }

        if ("PAUSADA".equals(operacional.statusOperacional())) {
            LocalDateTime agora = protecaoService.agora();
            return whatsappSessionRepository.findByIdOrganizacao(idOrganizacao)
                    .map(sessao -> sessao.getDtPausadoAte() == null || !sessao.getDtPausadoAte().isAfter(agora))
                    .orElse(true);
        }

        return false;
    }

    private StatusEnvioOrganizacaoResponse montarBloqueioProtecao(
            CanalNotificacao canal,
            DecisaoProtecaoNotificacao decisao,
            SessaoOperacionalContextoDTO operacional) {
        LocalDateTime retomada = decisao.tentarNovamenteEm();
        long segundos = retomada == null
                ? 0L
                : Math.max(0L, Duration.between(protecaoService.agora(), retomada).getSeconds());
        String textoRetomada = retomada == null
                ? "Aguarde a liberacao manual da operacao."
                : EstimativaTempoEnvioService.formatarTempo(segundos);

        return new StatusEnvioOrganizacaoResponse(
                false,
                canal,
                retomada,
                textoRetomada,
                titulo(decisao.motivo(), operacional),
                decisao.motivo(),
                orientacao(decisao.motivo(), operacional),
                operacional);
    }

    private StatusEnvioOrganizacaoResponse montarBloqueioOperacional(
            CanalNotificacao canal,
            SessaoOperacionalContextoDTO operacional) {
        Long idOrganizacao = tenantContextService.idOrganizacaoObrigatoria();
        LocalDateTime agora = protecaoService.agora();
        LocalDateTime retomada = whatsappSessionRepository.findByIdOrganizacao(idOrganizacao)
                .map(WhatsappSession::getDtPausadoAte)
                .filter(data -> data != null && data.isAfter(agora))
                .orElse(null);

        long segundos = retomada == null ? 0L : Duration.between(agora, retomada).getSeconds();
        String textoRetomada = retomada == null
                ? "Requer reativacao manual na pagina WhatsApp"
                : EstimativaTempoEnvioService.formatarTempo(segundos);

        return new StatusEnvioOrganizacaoResponse(
                false,
                canal,
                retomada,
                textoRetomada,
                operacional.titulo(),
                operacional.explicacao(),
                operacional.orientacao(),
                operacional);
    }

    private String titulo(String motivo, SessaoOperacionalContextoDTO operacional) {
        if (operacional != null && operacional.titulo() != null && !operacional.titulo().isBlank()) {
            return operacional.titulo();
        }
        if (motivo == null || motivo.isBlank()) {
            return "Envios temporariamente indisponiveis";
        }
        if (motivo.toLowerCase().contains("janela")) {
            return "Fora da janela de envio";
        }
        if (motivo.toLowerCase().contains("rate limit")) {
            return "Limite de envio atingido";
        }
        if (motivo.toLowerCase().contains("whatsapp")) {
            return "Sessao WhatsApp indisponivel";
        }
        return "Envios temporariamente indisponiveis";
    }

    private String orientacao(String motivo, SessaoOperacionalContextoDTO operacional) {
        if (operacional != null && operacional.orientacao() != null && !operacional.orientacao().isBlank()) {
            return operacional.orientacao();
        }
        if (motivo == null || motivo.isBlank()) {
            return "Aguarde a retomada automatica ou verifique a sessao WhatsApp.";
        }
        return motivo;
    }
}
