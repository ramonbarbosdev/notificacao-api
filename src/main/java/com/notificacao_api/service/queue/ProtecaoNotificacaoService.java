package com.notificacao_api.service.queue;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.stereotype.Service;

import com.notificacao_api.config.PropriedadesProtecaoNotificacao;
import com.notificacao_api.enums.CanalNotificacao;
import com.notificacao_api.enums.StatusOperacionalSessao;
import com.notificacao_api.enums.StatusNotificacao;
import com.notificacao_api.model.Notificacao;
import com.notificacao_api.model.OrganizacaoConfiguracao;
import com.notificacao_api.model.WhatsappSession;
import com.notificacao_api.repository.NotificacaoRepository;
import com.notificacao_api.repository.OrganizacaoConfiguracaoRepository;
import com.notificacao_api.repository.WhatsappSessionRepository;
import com.notificacao_api.shared.TelefoneBrasilUtil;

@Service
public class ProtecaoNotificacaoService {

    private final PropriedadesProtecaoNotificacao propriedades;
    private final NotificacaoRepository notificacaoRepository;
    private final WhatsappSessionRepository whatsappSessionRepository;
    private final OrganizacaoConfiguracaoRepository organizacaoConfiguracaoRepository;
    private final SegurancaOperacionalWhatsappService segurancaOperacionalWhatsappService;

    public ProtecaoNotificacaoService(
            PropriedadesProtecaoNotificacao propriedades,
            NotificacaoRepository notificacaoRepository,
            WhatsappSessionRepository whatsappSessionRepository,
            OrganizacaoConfiguracaoRepository organizacaoConfiguracaoRepository,
            SegurancaOperacionalWhatsappService segurancaOperacionalWhatsappService) {
        this.propriedades = propriedades;
        this.notificacaoRepository = notificacaoRepository;
        this.whatsappSessionRepository = whatsappSessionRepository;
        this.organizacaoConfiguracaoRepository = organizacaoConfiguracaoRepository;
        this.segurancaOperacionalWhatsappService = segurancaOperacionalWhatsappService;
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
        String base = idOrganizacao + "|" + canal + "|" + normalizarDestino(canal, destinatario) + "|" + mensagem.trim();
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            return java.util.HexFormat.of().formatHex(digest.digest(base.getBytes()));
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 indisponivel", ex);
        }
    }

    public boolean existeDuplicidadeRecente(
            Long idOrganizacao,
            CanalNotificacao canal,
            String destinatario,
            String hashDeduplicacao) {
        String destinatarioNormalizado = normalizarDestino(canal, destinatario);
        return notificacaoRepository.existsByIdOrganizacaoAndCanalAndDestinatarioAndHashDeduplicacaoAndDtCriacaoAfterAndStatusIn(
                idOrganizacao,
                canal,
                destinatarioNormalizado,
                hashDeduplicacao,
                agora().minusMinutes(propriedades.janelaDuplicidadeMinutos()),
                List.of(
                        StatusNotificacao.PENDENTE,
                        StatusNotificacao.PROCESSANDO,
                        StatusNotificacao.ENVIADA,
                        StatusNotificacao.ENTREGUE,
                        StatusNotificacao.LIDA));
    }

    public long delayAleatorioMillis(Long idOrganizacao) {
        long minimo = delayMinimoSegundos(idOrganizacao);
        long maximo = delayMaximoSegundos(idOrganizacao);
        long segundos = ThreadLocalRandom.current().nextLong(minimo, maximo + 1);
        return segundos * 1000L;
    }

    public LocalDateTime calcularProximaTentativa(int tentativa, Long idOrganizacao) {
        int intervaloBase = organizacaoConfiguracaoRepository.findByIdOrganizacao(idOrganizacao)
                .map(OrganizacaoConfiguracao::getRetryIntervaloSegundos)
                .filter(valor -> valor != null && valor > 0)
                .orElse(30);
        long segundos = Math.min(3600L, (long) Math.pow(2, Math.max(1, tentativa)) * intervaloBase);
        return agora().plusSeconds(segundos);
    }

    public LocalDateTime agora() {
        return LocalDateTime.now(java.time.ZoneId.of(propriedades.fusoHorario()));
    }

    public PropriedadesProtecaoNotificacao propriedades() {
        return propriedades;
    }

    public String normalizarDestino(CanalNotificacao canal, String destinatario) {
        return TelefoneBrasilUtil.normalizarDestino(canal, destinatario);
    }

    private DecisaoProtecaoNotificacao validarJanela(LocalDateTime agora) {
        java.time.LocalTime hora = agora.toLocalTime();
        if (!hora.isBefore(propriedades.inicioPermitido()) && hora.isBefore(propriedades.fimPermitido())) {
            return DecisaoProtecaoNotificacao.permitir();
        }

        java.time.LocalDate proximaData = hora.isBefore(propriedades.inicioPermitido())
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
        segurancaOperacionalWhatsappService.aplicarDecaimentoSeNecessario(sessao);

        if (sessao.getStatusOperacional() == StatusOperacionalSessao.BLOQUEADA) {
            LocalDateTime retomada = sessao.getDtPausadoAte() != null && sessao.getDtPausadoAte().isAfter(agora)
                    ? sessao.getDtPausadoAte()
                    : agora.plusMinutes(30);
            return DecisaoProtecaoNotificacao.aguardarAte(
                    retomada,
                    "Sessao WhatsApp bloqueada por protecao operacional. Reative manualmente apos corrigir a causa.");
        }

        if (sessao.getStatusOperacional() == StatusOperacionalSessao.RISCO_BANIMENTO) {
            LocalDateTime retomada = sessao.getDtPausadoAte() != null && sessao.getDtPausadoAte().isAfter(agora)
                    ? sessao.getDtPausadoAte()
                    : agora.plusMinutes(30);
            return DecisaoProtecaoNotificacao.aguardarAte(
                    retomada,
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
        int limiteMinuto = limitePorMinuto(notificacao.getIdOrganizacao());
        int limiteDia = limitePorDia(notificacao.getIdOrganizacao());

        long minuto = contarEnviadas(notificacao, agora.minusMinutes(1));
        if (minuto >= limiteMinuto) {
            return DecisaoProtecaoNotificacao.aguardarAte(agora.plusMinutes(1), "Rate limit por minuto atingido.");
        }

        long hora = contarEnviadas(notificacao, agora.minusHours(1));
        if (hora >= propriedades.limitePorHora()) {
            return DecisaoProtecaoNotificacao.aguardarAte(agora.plusMinutes(10), "Rate limit por hora atingido.");
        }

        long dia = contarEnviadas(notificacao, agora.minusDays(1));
        if (dia >= limiteDia) {
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

    private long delayMinimoSegundos(Long idOrganizacao) {
        return organizacaoConfiguracaoRepository.findByIdOrganizacao(idOrganizacao)
                .map(OrganizacaoConfiguracao::getWhatsappDelayMinSegundos)
                .filter(valor -> valor != null && valor > 0)
                .map(Integer::longValue)
                .orElse(propriedades.delayMinimoSegundos());
    }

    private long delayMaximoSegundos(Long idOrganizacao) {
        long maximo = organizacaoConfiguracaoRepository.findByIdOrganizacao(idOrganizacao)
                .map(OrganizacaoConfiguracao::getWhatsappDelayMaxSegundos)
                .filter(valor -> valor != null && valor > 0)
                .map(Integer::longValue)
                .orElse(propriedades.delayMaximoSegundos());
        long minimo = delayMinimoSegundos(idOrganizacao);
        return Math.max(maximo, minimo);
    }

    private int limitePorMinuto(Long idOrganizacao) {
        return organizacaoConfiguracaoRepository.findByIdOrganizacao(idOrganizacao)
                .map(OrganizacaoConfiguracao::getWhatsappLimitePorMinuto)
                .filter(valor -> valor != null && valor > 0)
                .orElse(propriedades.limitePorMinuto());
    }

    private int limitePorDia(Long idOrganizacao) {
        return organizacaoConfiguracaoRepository.findByIdOrganizacao(idOrganizacao)
                .map(OrganizacaoConfiguracao::getWhatsappLimitePorDia)
                .filter(valor -> valor != null && valor > 0)
                .orElse(propriedades.limitePorDia());
    }
}
