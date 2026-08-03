package com.notificacao_api.service.queue;

import java.time.LocalDateTime;
import java.time.ZoneId;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.notificacao_api.config.PropriedadesProtecaoNotificacao;
import com.notificacao_api.enums.CanalNotificacao;
import com.notificacao_api.enums.StatusOperacionalSessao;
import com.notificacao_api.model.Notificacao;
import com.notificacao_api.model.WhatsappSession;
import com.notificacao_api.repository.WhatsappSessionRepository;
import com.notificacao_api.service.AlertaOperacionalService;

@Service
public class SegurancaOperacionalWhatsappService {

    private final WhatsappSessionRepository whatsappSessionRepository;
    private final ProtecaoOperacionalConfigResolver configResolver;
    private final PropriedadesProtecaoNotificacao propriedades;
    private final AlertaOperacionalService alertaOperacionalService;

    public SegurancaOperacionalWhatsappService(
            WhatsappSessionRepository whatsappSessionRepository,
            ProtecaoOperacionalConfigResolver configResolver,
            PropriedadesProtecaoNotificacao propriedades,
            AlertaOperacionalService alertaOperacionalService) {
        this.whatsappSessionRepository = whatsappSessionRepository;
        this.configResolver = configResolver;
        this.propriedades = propriedades;
        this.alertaOperacionalService = alertaOperacionalService;
    }

    @Transactional
    public void registrarSucesso(Notificacao notificacao, LocalDateTime proximoEnvioApos) {
        if (notificacao.getCanal() != CanalNotificacao.WHATSAPP) {
            return;
        }

        WhatsappSession sessao = whatsappSessionRepository.findByIdOrganizacao(notificacao.getIdOrganizacao())
                .orElse(null);
        if (sessao == null) {
            return;
        }

        sessao.setFalhasConsecutivas(0);
        sessao.setDtUltimaFalha(null);
        sessao.setDtProximoEnvioApos(proximoEnvioApos);

        LocalDateTime agora = agora();
        if (sessao.getStatusOperacional() != StatusOperacionalSessao.BLOQUEADA
                && (sessao.getDtPausadoAte() == null || !sessao.getDtPausadoAte().isAfter(agora))) {
            sessao.setStatusOperacional(StatusOperacionalSessao.ATIVA);
            sessao.setDtPausadoAte(null);
        }

        whatsappSessionRepository.save(sessao);
    }

    @Transactional
    public void registrarFalha(Notificacao notificacao, String ultimoErro) {
        if (notificacao.getCanal() != CanalNotificacao.WHATSAPP) {
            return;
        }

        WhatsappSession sessao = whatsappSessionRepository.findByIdOrganizacao(notificacao.getIdOrganizacao())
                .orElse(null);
        if (sessao == null) {
            return;
        }

        LocalDateTime agora = agora();
        aplicarDecaimentoFalhas(sessao, agora);

        int falhas = sessao.getFalhasConsecutivas() == null ? 1 : sessao.getFalhasConsecutivas() + 1;
        sessao.setFalhasConsecutivas(falhas);
        sessao.setDtUltimaFalha(agora);

        int limite = configResolver.limiteFalhasSessao(notificacao.getIdOrganizacao());
        int limiteBloqueio = limite * configResolver.limiteBloqueioSessaoMultiplicador();
        StatusOperacionalSessao statusAnterior = sessao.getStatusOperacional();
        boolean riscoBanimento = falhas >= limite;
        boolean bloqueioManualNecessario = falhas >= limiteBloqueio;

        if (bloqueioManualNecessario) {
            sessao.setStatusOperacional(StatusOperacionalSessao.BLOQUEADA);
            sessao.setDtPausadoAte(agora.plusSeconds(configResolver.pausaRiscoSegundos()));
        } else if (riscoBanimento) {
            sessao.setStatusOperacional(StatusOperacionalSessao.RISCO_BANIMENTO);
            sessao.setDtPausadoAte(agora.plusSeconds(configResolver.pausaRiscoSegundos()));
        } else {
            sessao.setStatusOperacional(StatusOperacionalSessao.PAUSADA);
            sessao.setDtPausadoAte(agora.plusSeconds(configResolver.pausaAutomaticaSegundos()));
        }

        whatsappSessionRepository.save(sessao);

        if (statusAnterior != sessao.getStatusOperacional()) {
            try {
                alertaOperacionalService.registrarPausaWhatsappAposFalha(
                        notificacao,
                        ultimoErro,
                        riscoBanimento || bloqueioManualNecessario);
            } catch (Exception ex) {
                // nao interrompe o fluxo da fila
            }
        }
    }

    @Transactional
    public void reativarSessao(Long idOrganizacao) {
        WhatsappSession sessao = whatsappSessionRepository.findByIdOrganizacao(idOrganizacao)
                .orElseGet(() -> {
                    WhatsappSession nova = new WhatsappSession();
                    nova.setIdOrganizacao(idOrganizacao);
                    nova.setTpStatus(com.notificacao_api.enums.WhatsappSessionStatus.NAO_INICIADO);
                    nova.setDsSessionPath("organizacao-" + idOrganizacao);
                    return nova;
                });

        sessao.setStatusOperacional(StatusOperacionalSessao.ATIVA);
        sessao.setFalhasConsecutivas(0);
        sessao.setDtUltimaFalha(null);
        sessao.setDtPausadoAte(null);
        whatsappSessionRepository.save(sessao);
    }

    @Transactional
    public void aplicarDecaimentoSeNecessario(WhatsappSession sessao) {
        aplicarDecaimentoFalhas(sessao, agora());
        if (sessao.getFalhasConsecutivas() != null && sessao.getFalhasConsecutivas() > 0) {
            whatsappSessionRepository.save(sessao);
        }
    }

    private LocalDateTime agora() {
        return LocalDateTime.now(ZoneId.of(propriedades.fusoHorario()));
    }

    private void aplicarDecaimentoFalhas(WhatsappSession sessao, LocalDateTime agora) {
        if (sessao.getFalhasConsecutivas() == null || sessao.getFalhasConsecutivas() <= 0) {
            return;
        }
        if (sessao.getDtUltimaFalha() == null) {
            return;
        }

        long decaimentoMinutos = configResolver.decaimentoFalhasMinutos();
        LocalDateTime limiteDecaimento = sessao.getDtUltimaFalha().plusMinutes(decaimentoMinutos);
        if (!agora.isAfter(limiteDecaimento)) {
            return;
        }

        long periodos = java.time.Duration.between(sessao.getDtUltimaFalha(), agora).toMinutes() / decaimentoMinutos;
        if (periodos <= 0) {
            return;
        }

        int reducao = (int) Math.min(periodos, sessao.getFalhasConsecutivas());
        int novasFalhas = sessao.getFalhasConsecutivas() - reducao;
        sessao.setFalhasConsecutivas(Math.max(0, novasFalhas));

        if (novasFalhas == 0) {
            sessao.setDtUltimaFalha(null);
            if (sessao.getStatusOperacional() == StatusOperacionalSessao.PAUSADA
                    && (sessao.getDtPausadoAte() == null || !sessao.getDtPausadoAte().isAfter(agora))) {
                sessao.setStatusOperacional(StatusOperacionalSessao.ATIVA);
                sessao.setDtPausadoAte(null);
            }
        }
    }
}
