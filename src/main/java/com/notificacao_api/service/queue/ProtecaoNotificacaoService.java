package com.notificacao_api.service.queue;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.stereotype.Service;

import com.notificacao_api.config.PropriedadesProtecaoNotificacao;
import com.notificacao_api.enums.CanalNotificacao;
import com.notificacao_api.enums.StatusOperacionalSessao;
import com.notificacao_api.enums.StatusNotificacao;
import com.notificacao_api.model.Notificacao;
import com.notificacao_api.model.WhatsappSession;
import com.notificacao_api.repository.NotificacaoRepository;
import com.notificacao_api.repository.WhatsappSessionRepository;

@Service
public class ProtecaoNotificacaoService {

    private final PropriedadesProtecaoNotificacao propriedades;
    private final NotificacaoRepository notificacaoRepository;
    private final WhatsappSessionRepository whatsappSessionRepository;

    public ProtecaoNotificacaoService(
            PropriedadesProtecaoNotificacao propriedades,
            NotificacaoRepository notificacaoRepository,
            WhatsappSessionRepository whatsappSessionRepository) {
        this.propriedades = propriedades;
        this.notificacaoRepository = notificacaoRepository;
        this.whatsappSessionRepository = whatsappSessionRepository;
    }

    public DecisaoProtecaoNotificacao avaliar(Notificacao notificacao) {
        LocalDateTime agora = agora();

        DecisaoProtecaoNotificacao janela = validarJanela(agora);
        if (!janela.permitida()) {
            return janela;
        }

        if (notificacao.getCanal() == CanalNotificacao.WHATSAPP) {
            DecisaoProtecaoNotificacao sessao = validarSessaoWhatsapp(notificacao.getIdOrganizacao(), agora);
            if (!sessao.permitida()) {
                return sessao;
            }
        }

        DecisaoProtecaoNotificacao rateLimit = validarRateLimit(notificacao, agora);
        if (!rateLimit.permitida()) {
            return rateLimit;
        }

        return DecisaoProtecaoNotificacao.permitir();
    }

    public String gerarHashDeduplicacao(Long idOrganizacao, CanalNotificacao canal, String destinatario, String mensagem) {
        String base = idOrganizacao + "|" + canal + "|" + normalizarDestino(destinatario) + "|" + mensagem.trim();
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(base.getBytes()));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 indisponivel", ex);
        }
    }

    public boolean existeDuplicidadeRecente(
            Long idOrganizacao,
            CanalNotificacao canal,
            String destinatario,
            String hashDeduplicacao) {
        return notificacaoRepository.existsByIdOrganizacaoAndCanalAndDestinatarioAndHashDeduplicacaoAndDtCriacaoAfterAndStatusIn(
                idOrganizacao,
                canal,
                destinatario,
                hashDeduplicacao,
                agora().minusMinutes(propriedades.janelaDuplicidadeMinutos()),
                List.of(
                        StatusNotificacao.PENDENTE,
                        StatusNotificacao.PROCESSANDO,
                        StatusNotificacao.ENVIADA,
                        StatusNotificacao.ENTREGUE,
                        StatusNotificacao.LIDA));
    }

    public long delayAleatorioMillis() {
        long minimo = propriedades.delayMinimoSegundos();
        long maximo = propriedades.delayMaximoSegundos();
        long segundos = ThreadLocalRandom.current().nextLong(minimo, maximo + 1);
        return segundos * 1000L;
    }

    public LocalDateTime calcularProximaTentativa(int tentativa) {
        long segundos = Math.min(3600L, (long) Math.pow(2, Math.max(1, tentativa)) * 30L);
        return agora().plusSeconds(segundos);
    }

    public LocalDateTime agora() {
        return LocalDateTime.now(ZoneId.of(propriedades.fusoHorario()));
    }

    public PropriedadesProtecaoNotificacao propriedades() {
        return propriedades;
    }

    private DecisaoProtecaoNotificacao validarJanela(LocalDateTime agora) {
        LocalTime hora = agora.toLocalTime();
        if (!hora.isBefore(propriedades.inicioPermitido()) && hora.isBefore(propriedades.fimPermitido())) {
            return DecisaoProtecaoNotificacao.permitir();
        }

        LocalDate proximaData = hora.isBefore(propriedades.inicioPermitido())
                ? agora.toLocalDate()
                : agora.toLocalDate().plusDays(1);
        return DecisaoProtecaoNotificacao.aguardarAte(
                LocalDateTime.of(proximaData, propriedades.inicioPermitido()),
                "Fora da janela de envio configurada.");
    }

    private DecisaoProtecaoNotificacao validarSessaoWhatsapp(Long idOrganizacao, LocalDateTime agora) {
        return whatsappSessionRepository.findByIdOrganizacao(idOrganizacao)
                .map(sessao -> avaliarSessao(sessao, agora))
                .orElse(DecisaoProtecaoNotificacao.permitir());
    }

    private DecisaoProtecaoNotificacao avaliarSessao(WhatsappSession sessao, LocalDateTime agora) {
        if (sessao.getStatusOperacional() == StatusOperacionalSessao.BLOQUEADA
                || sessao.getStatusOperacional() == StatusOperacionalSessao.RISCO_BANIMENTO) {
            return DecisaoProtecaoNotificacao.aguardarAte(
                    agora.plusMinutes(30),
                    "Sessao WhatsApp em estado de risco operacional.");
        }

        if (sessao.getStatusOperacional() == StatusOperacionalSessao.PAUSADA
                && sessao.getDtPausadoAte() != null
                && sessao.getDtPausadoAte().isAfter(agora)) {
            return DecisaoProtecaoNotificacao.aguardarAte(
                    sessao.getDtPausadoAte(),
                    "Sessao WhatsApp pausada automaticamente.");
        }

        if (sessao.getDtProximoEnvioApos() != null && sessao.getDtProximoEnvioApos().isAfter(agora)) {
            return DecisaoProtecaoNotificacao.aguardarAte(
                    sessao.getDtProximoEnvioApos(),
                    "Delay entre envios em andamento.");
        }

        return DecisaoProtecaoNotificacao.permitir();
    }

    private DecisaoProtecaoNotificacao validarRateLimit(Notificacao notificacao, LocalDateTime agora) {
        long minuto = contarEnviadas(notificacao, agora.minusMinutes(1));
        if (minuto >= propriedades.limitePorMinuto()) {
            return DecisaoProtecaoNotificacao.aguardarAte(agora.plusMinutes(1), "Rate limit por minuto atingido.");
        }

        long hora = contarEnviadas(notificacao, agora.minusHours(1));
        if (hora >= propriedades.limitePorHora()) {
            return DecisaoProtecaoNotificacao.aguardarAte(agora.plusMinutes(10), "Rate limit por hora atingido.");
        }

        long dia = contarEnviadas(notificacao, agora.minusDays(1));
        if (dia >= propriedades.limitePorDia()) {
            return DecisaoProtecaoNotificacao.aguardarAte(agora.plusHours(1), "Rate limit diario atingido.");
        }

        return DecisaoProtecaoNotificacao.permitir();
    }

    private long contarEnviadas(Notificacao notificacao, LocalDateTime desde) {
        return notificacaoRepository.countByIdOrganizacaoAndCanalAndStatusAndDtEnvioAfter(
                notificacao.getIdOrganizacao(),
                notificacao.getCanal(),
                StatusNotificacao.ENVIADA,
                desde);
    }

    private String normalizarDestino(String destinatario) {
        return destinatario == null ? "" : destinatario.replaceAll("\\D", "");
    }
}
