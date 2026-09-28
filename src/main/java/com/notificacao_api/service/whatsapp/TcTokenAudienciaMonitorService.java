package com.notificacao_api.service.whatsapp;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import com.notificacao_api.dto.whatsapp.TcTokenAudienciaKpisResponse;
import com.notificacao_api.dto.whatsapp.TcTokenAudienciaLinhaResponse;
import com.notificacao_api.dto.whatsapp.TcTokenAudienciaScanResponse;
import com.notificacao_api.enums.CanalNotificacao;
import com.notificacao_api.enums.StatusNotificacao;
import com.notificacao_api.enums.TcTokenAudienciaOrigem;
import com.notificacao_api.enums.TcTokenAudienciaSituacao;
import com.notificacao_api.enums.WhatsappSessionStatus;
import com.notificacao_api.model.OrganizacaoGithubResponsavel;
import com.notificacao_api.repository.NotificacaoRepository;
import com.notificacao_api.repository.OrganizacaoGithubResponsavelRepository;
import com.notificacao_api.repository.WhatsappSessionRepository;
import com.notificacao_api.shared.TelefoneBrasilUtil;

@Service
public class TcTokenAudienciaMonitorService {

    private static final Logger log = LoggerFactory.getLogger(TcTokenAudienciaMonitorService.class);
    private static final int GATEWAY_LOTE_MAX = 200;

    private final OrganizacaoGithubResponsavelRepository githubResponsavelRepository;
    private final NotificacaoRepository notificacaoRepository;
    private final WhatsappSessionRepository whatsappSessionRepository;
    private final WhatsAppGatewayClient gatewayClient;

    private final int vidaDias;
    private final int janelaAlertaDias;
    private final int filaLookbackDias;
    private final int emUsoEnviadoLookbackDias;
    private final long refreshMinIntervaloSegundos;
    private final long autoGatewayMinIntervaloSegundos;
    private final boolean gatewayAudienciaCompleta;

    private final ConcurrentHashMap<Long, TcTokenAudienciaScanResponse> cachePorOrganizacao = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Instant> ultimoRefreshManual = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Instant> ultimoAutoGateway = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Boolean> varreduraGatewayEmAndamento = new ConcurrentHashMap<>();

    private static final List<StatusNotificacao> STATUS_ENVIO_EFETIVO =
            List.of(StatusNotificacao.ENVIADA, StatusNotificacao.ENTREGUE, StatusNotificacao.LIDA);

    public TcTokenAudienciaMonitorService(
            OrganizacaoGithubResponsavelRepository githubResponsavelRepository,
            NotificacaoRepository notificacaoRepository,
            WhatsappSessionRepository whatsappSessionRepository,
            WhatsAppGatewayClient gatewayClient,
            @Value("${notificacao.tctoken.vida-dias:28}") int vidaDias,
            @Value("${notificacao.tctoken.janela-alerta-dias:7}") int janelaAlertaDias,
            @Value("${notificacao.tctoken.fila-lookback-dias:60}") int filaLookbackDias,
            @Value("${notificacao.tctoken.em-uso-enviado-lookback-dias:30}") int emUsoEnviadoLookbackDias,
            @Value("${notificacao.tctoken.audiencia.refresh-min-intervalo-segundos:60}") long refreshMinIntervaloSegundos,
            @Value("${notificacao.tctoken.audiencia.auto-gateway-min-segundos:300}") long autoGatewayMinIntervaloSegundos,
            @Value("${notificacao.tctoken.audiencia.gateway-audiencia-completa:false}") boolean gatewayAudienciaCompleta) {
        this.githubResponsavelRepository = githubResponsavelRepository;
        this.notificacaoRepository = notificacaoRepository;
        this.whatsappSessionRepository = whatsappSessionRepository;
        this.gatewayClient = gatewayClient;
        this.vidaDias = vidaDias;
        this.janelaAlertaDias = janelaAlertaDias;
        this.filaLookbackDias = filaLookbackDias;
        this.emUsoEnviadoLookbackDias = emUsoEnviadoLookbackDias;
        this.refreshMinIntervaloSegundos = refreshMinIntervaloSegundos;
        this.autoGatewayMinIntervaloSegundos = Math.max(60, autoGatewayMinIntervaloSegundos);
        this.gatewayAudienciaCompleta = gatewayAudienciaCompleta;
    }

    public TcTokenAudienciaScanResponse obter(Long idOrganizacao, boolean forceRefresh) {
        if (forceRefresh) {
            validarIntervaloRefresh(idOrganizacao);
            TcTokenAudienciaScanResponse scan = executarScanComGateway(idOrganizacao);
            cachePorOrganizacao.put(idOrganizacao, scan);
            ultimoRefreshManual.put(idOrganizacao, Instant.now());
            ultimoAutoGateway.put(idOrganizacao, Instant.now());
            return scan;
        }

        TcTokenAudienciaScanResponse cached = cachePorOrganizacao.get(idOrganizacao);
        if (cached != null && cached.varreduraGatewayCompleta()) {
            return cached;
        }

        if (sessaoWhatsappConectada(idOrganizacao) && podeEnriquecerAutomaticamente(idOrganizacao)) {
            try {
                TcTokenAudienciaScanResponse scan = executarScanComGateway(idOrganizacao);
                cachePorOrganizacao.put(idOrganizacao, scan);
                ultimoAutoGateway.put(idOrganizacao, Instant.now());
                return scan;
            } catch (ResponseStatusException ex) {
                if (ex.getStatusCode() == HttpStatus.CONFLICT && cached != null) {
                    return cached;
                }
                throw ex;
            }
        }

        if (cached != null) {
            return cached;
        }

        return montarRespostaSomenteAudiencia(idOrganizacao);
    }

    public void atualizarCache(Long idOrganizacao) {
        if (!iniciarVarreduraGateway(idOrganizacao)) {
            log.debug("Varredura tctoken audiencia org={} ja em andamento; job ignorado", idOrganizacao);
            return;
        }
        try {
            cachePorOrganizacao.put(idOrganizacao, executarScanInterno(idOrganizacao, true));
        } finally {
            finalizarVarreduraGateway(idOrganizacao);
        }
    }

    /** Varredura completa (audiencia + gateway). Usado por job, refresh manual e testes. */
    public TcTokenAudienciaScanResponse executarScan(Long idOrganizacao) {
        return executarScanComGateway(idOrganizacao);
    }

    public boolean telefoneNaAudienciaEmUso(Long idOrganizacao, String telefoneNormalizado) {
        Map<String, AudienciaEntry> audiencia = montarAudiencia(idOrganizacao);
        if (!audiencia.containsKey(telefoneNormalizado)) {
            return false;
        }
        return resolverTelefonesConsultaGateway(idOrganizacao, audiencia).contains(telefoneNormalizado);
    }

    /** Qualquer entrada da lista (GitHub ou fila no lookback), sem exigir "em uso". */
    public boolean telefoneNaAudiencia(Long idOrganizacao, String telefoneNormalizado) {
        return montarAudiencia(idOrganizacao).containsKey(telefoneNormalizado);
    }

    public Optional<TcTokenAudienciaLinhaResponse> buscarLinhaNoCache(
            Long idOrganizacao, String telefoneNormalizado) {
        TcTokenAudienciaScanResponse cached = cachePorOrganizacao.get(idOrganizacao);
        if (cached == null) {
            return Optional.empty();
        }
        return cached.linhas().stream()
                .filter(linha -> telefoneNormalizado.equals(linha.telefone()))
                .findFirst();
    }

    /**
     * Inbound/outbound na sessao pode renovar tctoken no gateway; atualiza a linha na monitoracao sem esperar
     * o intervalo de cache (5 min) ou refresh manual.
     */
    public void reagirAtividadeConversa(Long idOrganizacao, String telefoneNormalizado) {
        if (idOrganizacao == null || !StringUtils.hasText(telefoneNormalizado)) {
            return;
        }
        if (!telefoneNaAudienciaEmUso(idOrganizacao, telefoneNormalizado)) {
            return;
        }
        if (!sessaoWhatsappConectada(idOrganizacao)) {
            invalidarCacheOrganizacao(idOrganizacao);
            return;
        }
        if (!iniciarVarreduraGateway(idOrganizacao)) {
            invalidarCacheOrganizacao(idOrganizacao);
            return;
        }
        try {
            mesclarGatewayTelefone(idOrganizacao, telefoneNormalizado);
        } catch (Exception ex) {
            log.debug(
                    "Falha ao atualizar tctoken audiencia apos atividade org={} telefone={}: {}",
                    idOrganizacao,
                    telefoneNormalizado,
                    ex.getMessage());
            invalidarCacheOrganizacao(idOrganizacao);
        } finally {
            finalizarVarreduraGateway(idOrganizacao);
        }
    }

    private void invalidarCacheOrganizacao(Long idOrganizacao) {
        cachePorOrganizacao.remove(idOrganizacao);
        ultimoAutoGateway.remove(idOrganizacao);
    }

    private void mesclarGatewayTelefone(Long idOrganizacao, String telefone) {
        Map<String, AudienciaEntry> audiencia = montarAudiencia(idOrganizacao);
        AudienciaEntry entry = audiencia.get(telefone);
        if (entry == null) {
            return;
        }

        ConsultaGatewayLote consulta = consultarGatewayEmLotes(idOrganizacao, List.of(telefone));
        TcTokenAudienciaLinhaResponse linhaAtualizada = montarLinha(
                telefone, entry, consulta.porTelefone(), consulta.gatewayOnline(), true);

        TcTokenAudienciaScanResponse cached = cachePorOrganizacao.get(idOrganizacao);
        if (cached == null) {
            TcTokenAudienciaScanResponse scan = executarScanInterno(idOrganizacao, true);
            cachePorOrganizacao.put(idOrganizacao, scan);
            ultimoAutoGateway.put(idOrganizacao, Instant.now());
            return;
        }

        List<TcTokenAudienciaLinhaResponse> linhas = new ArrayList<>(cached.linhas());
        linhas.removeIf(l -> telefone.equals(l.telefone()));
        linhas.add(linhaAtualizada);
        linhas.sort(comparadorLinhas());

        boolean gatewayOnline = cached.gatewayOnline() || consulta.gatewayOnline();
        TcTokenAudienciaKpisResponse kpis = calcularKpis(linhas);
        TcTokenAudienciaScanResponse atualizado = new TcTokenAudienciaScanResponse(
                cached.sucesso(),
                cached.erro(),
                idOrganizacao,
                Instant.now(),
                gatewayOnline,
                cached.vidaDiasToken(),
                cached.janelaAlertaDias(),
                cached.filaLookbackDias(),
                cached.varreduraGatewayCompleta(),
                kpis,
                linhas);
        cachePorOrganizacao.put(idOrganizacao, atualizado);
        ultimoAutoGateway.put(idOrganizacao, Instant.now());
    }

    private TcTokenAudienciaScanResponse executarScanComGateway(Long idOrganizacao) {
        if (!iniciarVarreduraGateway(idOrganizacao)) {
            TcTokenAudienciaScanResponse cached = cachePorOrganizacao.get(idOrganizacao);
            if (cached != null && cached.varreduraGatewayCompleta()) {
                return cached;
            }
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Varredura tctoken em andamento. Tente novamente em instantes.");
        }
        try {
            return executarScanInterno(idOrganizacao, true);
        } finally {
            finalizarVarreduraGateway(idOrganizacao);
        }
    }

    private TcTokenAudienciaScanResponse montarRespostaSomenteAudiencia(Long idOrganizacao) {
        return executarScanInterno(idOrganizacao, false);
    }

    private TcTokenAudienciaScanResponse executarScanInterno(Long idOrganizacao, boolean consultarGateway) {
        Map<String, AudienciaEntry> audiencia = montarAudiencia(idOrganizacao);
        Set<String> telefonesConsultaGateway = resolverTelefonesConsultaGateway(idOrganizacao, audiencia);

        Map<String, Map<String, Object>> gatewayPorTelefone = Map.of();
        boolean gatewayOnline = false;

        if (consultarGateway && !telefonesConsultaGateway.isEmpty()) {
            ConsultaGatewayLote consulta =
                    consultarGatewayEmLotes(idOrganizacao, new ArrayList<>(telefonesConsultaGateway));
            gatewayOnline = consulta.gatewayOnline();
            gatewayPorTelefone = consulta.porTelefone();
        }

        List<TcTokenAudienciaLinhaResponse> linhas = new ArrayList<>();
        for (Map.Entry<String, AudienciaEntry> entry : audiencia.entrySet()) {
            boolean consultado = !consultarGateway || telefonesConsultaGateway.contains(entry.getKey());
            linhas.add(montarLinha(
                    entry.getKey(),
                    entry.getValue(),
                    gatewayPorTelefone,
                    gatewayOnline,
                    consultado));
        }

        linhas.sort(comparadorLinhas());

        TcTokenAudienciaKpisResponse kpis = calcularKpis(linhas);
        Instant atualizadoEm = Instant.now();

        if (kpis.proximoExpirar() > 0) {
            log.warn(
                    "TcToken audiencia org={}: {} contato(s) proximo(s) de expirar (janela {} dias)",
                    idOrganizacao,
                    kpis.proximoExpirar(),
                    janelaAlertaDias);
        }

        return new TcTokenAudienciaScanResponse(
                true,
                null,
                idOrganizacao,
                atualizadoEm,
                gatewayOnline,
                vidaDias,
                janelaAlertaDias,
                filaLookbackDias,
                consultarGateway,
                kpis,
                linhas);
    }

    private boolean iniciarVarreduraGateway(Long idOrganizacao) {
        return varreduraGatewayEmAndamento.putIfAbsent(idOrganizacao, Boolean.TRUE) == null;
    }

    private void finalizarVarreduraGateway(Long idOrganizacao) {
        varreduraGatewayEmAndamento.remove(idOrganizacao);
    }

    private void validarIntervaloRefresh(Long idOrganizacao) {
        Instant ultimo = ultimoRefreshManual.get(idOrganizacao);
        if (ultimo == null) {
            return;
        }
        long segundos = Duration.between(ultimo, Instant.now()).getSeconds();
        if (segundos < refreshMinIntervaloSegundos) {
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Aguarde antes de forcar nova varredura tctoken.");
        }
    }

    private Map<String, AudienciaEntry> montarAudiencia(Long idOrganizacao) {
        Map<String, AudienciaEntry> mapa = new LinkedHashMap<>();

        for (OrganizacaoGithubResponsavel responsavel :
                githubResponsavelRepository.findByIdOrganizacaoOrderByDtAtualizacaoDesc(idOrganizacao)) {
            if (!githubAtivoCompleto(responsavel)) {
                continue;
            }
            String telefone = normalizarTelefoneSeguro(responsavel.getNuWhatsapp());
            if (telefone == null) {
                continue;
            }
            mapa.computeIfAbsent(telefone, k -> new AudienciaEntry())
                    .origens.add(TcTokenAudienciaOrigem.GITHUB);
            mapa.get(telefone).githubLogin = responsavel.getDsGithubLogin();
        }

        LocalDateTime desde = LocalDateTime.now().minusDays(filaLookbackDias);
        for (String destinatario : notificacaoRepository.listarDestinatariosDistintosDesde(
                idOrganizacao, CanalNotificacao.WHATSAPP, desde)) {
            String telefone = normalizarTelefoneSeguro(destinatario);
            if (telefone == null) {
                continue;
            }
            mapa.computeIfAbsent(telefone, k -> new AudienciaEntry())
                    .origens.add(TcTokenAudienciaOrigem.FILA);
        }

        return mapa;
    }

    private Set<String> resolverTelefonesConsultaGateway(Long idOrganizacao, Map<String, AudienciaEntry> audiencia) {
        if (gatewayAudienciaCompleta) {
            return new HashSet<>(audiencia.keySet());
        }

        Set<String> emUso = new HashSet<>();

        for (Map.Entry<String, AudienciaEntry> entry : audiencia.entrySet()) {
            if (entry.getValue().origens.contains(TcTokenAudienciaOrigem.GITHUB)) {
                emUso.add(entry.getKey());
            }
        }

        LocalDateTime desdeEnvio = LocalDateTime.now().minusDays(emUsoEnviadoLookbackDias);
        for (String destinatario : notificacaoRepository.listarDestinatariosDistintosEnviadosDesde(
                idOrganizacao, CanalNotificacao.WHATSAPP, desdeEnvio, STATUS_ENVIO_EFETIVO)) {
            String telefone = normalizarTelefoneSeguro(destinatario);
            if (telefone != null && audiencia.containsKey(telefone)) {
                emUso.add(telefone);
            }
        }

        return emUso;
    }

    private boolean sessaoWhatsappConectada(Long idOrganizacao) {
        return whatsappSessionRepository
                .findByIdOrganizacao(idOrganizacao)
                .map(sessao -> sessao.getTpStatus() == WhatsappSessionStatus.CONECTADO)
                .orElse(false);
    }

    private boolean podeEnriquecerAutomaticamente(Long idOrganizacao) {
        Instant ultimo = ultimoAutoGateway.get(idOrganizacao);
        if (ultimo == null) {
            return true;
        }
        return Duration.between(ultimo, Instant.now()).getSeconds() >= autoGatewayMinIntervaloSegundos;
    }

    private ConsultaGatewayLote consultarGatewayEmLotes(Long idOrganizacao, List<String> telefones) {
        Map<String, Map<String, Object>> merged = new LinkedHashMap<>();
        boolean gatewayOnline = false;
        for (int offset = 0; offset < telefones.size(); offset += GATEWAY_LOTE_MAX) {
            int fim = Math.min(offset + GATEWAY_LOTE_MAX, telefones.size());
            List<String> lote = telefones.subList(offset, fim);
            Map<String, Object> respostaGateway = gatewayClient.consultarTcTokenAudiencia(idOrganizacao, lote);
            if (Boolean.TRUE.equals(respostaGateway.get("sucesso"))) {
                gatewayOnline = true;
                merged.putAll(indexarLinhasGateway(respostaGateway));
            }
        }
        return new ConsultaGatewayLote(gatewayOnline, merged);
    }

    private TcTokenAudienciaLinhaResponse montarLinha(
            String telefone,
            AudienciaEntry entry,
            Map<String, Map<String, Object>> gatewayPorTelefone,
            boolean gatewayOnline,
            boolean consultadoNoGateway) {
        Map<String, Object> gw = gatewayPorTelefone.get(telefone);

        Double idadeDias = null;
        Double expiraEmDias = null;
        Boolean prontoParaEnvio = null;
        String jidComToken = null;
        boolean tokenPresente = false;
        boolean expirado = true;

        if (consultadoNoGateway && gw != null && gatewayOnline) {
            idadeDias = asDouble(gw.get("idadeDias"));
            expiraEmDias = asDouble(gw.get("expiraEmDias"));
            tokenPresente = Boolean.TRUE.equals(gw.get("tokenPresente"));
            expirado = Boolean.TRUE.equals(gw.get("expirado"));
            prontoParaEnvio = Boolean.TRUE.equals(gw.get("prontoParaEnvio"));
            jidComToken = asString(gw.get("jidComToken"));
        }

        TcTokenAudienciaSituacao situacao =
                classificarSituacao(tokenPresente, expirado, expiraEmDias, gatewayOnline, consultadoNoGateway);

        String nome = entry.githubLogin != null ? "@" + entry.githubLogin : "—";

        return new TcTokenAudienciaLinhaResponse(
                telefone,
                mascararTelefone(telefone),
                nome,
                List.copyOf(entry.origens),
                idadeDias,
                expiraEmDias,
                situacao,
                rotuloSituacao(situacao, consultadoNoGateway),
                prontoParaEnvio,
                !gatewayOnline || !consultadoNoGateway,
                jidComToken,
                consultadoNoGateway);
    }

    private TcTokenAudienciaSituacao classificarSituacao(
            boolean tokenPresente,
            boolean expirado,
            Double expiraEmDias,
            boolean gatewayOnline,
            boolean consultadoNoGateway) {
        if (!consultadoNoGateway) {
            return TcTokenAudienciaSituacao.OK;
        }
        if (!gatewayOnline) {
            return TcTokenAudienciaSituacao.AUSENTE;
        }
        if (!tokenPresente) {
            return TcTokenAudienciaSituacao.AUSENTE;
        }
        if (expirado || (expiraEmDias != null && expiraEmDias <= 0)) {
            return TcTokenAudienciaSituacao.EXPIRADO;
        }
        if (expiraEmDias != null && expiraEmDias <= janelaAlertaDias) {
            return TcTokenAudienciaSituacao.PROXIMO_EXPIRAR;
        }
        return TcTokenAudienciaSituacao.OK;
    }

    private static Comparator<TcTokenAudienciaLinhaResponse> comparadorLinhas() {
        return Comparator
                .comparingInt(TcTokenAudienciaMonitorService::prioridadeSituacao)
                .thenComparing(l -> l.expiraEmDias() == null ? Double.MAX_VALUE : l.expiraEmDias())
                .thenComparing(TcTokenAudienciaLinhaResponse::telefone);
    }

    private static int prioridadeSituacao(TcTokenAudienciaLinhaResponse linha) {
        return switch (linha.situacao()) {
            case PROXIMO_EXPIRAR -> 0;
            case EXPIRADO -> 1;
            case AUSENTE -> 2;
            case OK -> 3;
        };
    }

    private TcTokenAudienciaKpisResponse calcularKpis(List<TcTokenAudienciaLinhaResponse> linhas) {
        int proximo = 0;
        int ausente = 0;
        int expirados = 0;
        int ok = 0;
        for (TcTokenAudienciaLinhaResponse linha : linhas) {
            if (!linha.consultadoNoGateway()) {
                continue;
            }
            switch (linha.situacao()) {
                case PROXIMO_EXPIRAR -> proximo++;
                case AUSENTE -> ausente++;
                case EXPIRADO -> expirados++;
                case OK -> ok++;
                default -> {
                }
            }
        }
        return new TcTokenAudienciaKpisResponse(linhas.size(), proximo, ausente, expirados, ok);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Map<String, Object>> indexarLinhasGateway(Map<String, Object> respostaGateway) {
        Object bruto = respostaGateway.get("linhas");
        if (!(bruto instanceof List<?> lista)) {
            return Map.of();
        }
        Map<String, Map<String, Object>> mapa = new LinkedHashMap<>();
        for (Object item : lista) {
            if (!(item instanceof Map<?, ?> raw)) {
                continue;
            }
            Map<String, Object> linha = (Map<String, Object>) raw;
            String telefone = asString(linha.get("telefone"));
            if (StringUtils.hasText(telefone)) {
                mapa.put(telefone, linha);
            }
        }
        return mapa;
    }

    private static boolean githubAtivoCompleto(OrganizacaoGithubResponsavel responsavel) {
        return Boolean.TRUE.equals(responsavel.getAtivo())
                && StringUtils.hasText(responsavel.getDsGithubLogin());
    }

    private String normalizarTelefoneSeguro(String bruto) {
        if (!StringUtils.hasText(bruto)) {
            return null;
        }
        try {
            String normalizado = TelefoneBrasilUtil.normalizarDestino(CanalNotificacao.WHATSAPP, bruto);
            if (!TelefoneBrasilUtil.celularBrasilComNonoDigito(normalizado)) {
                return null;
            }
            return normalizado;
        } catch (Exception ex) {
            return null;
        }
    }

    private static String mascararTelefone(String telefone) {
        if (telefone == null || telefone.length() < 8) {
            return telefone;
        }
        return telefone.substring(0, telefone.length() - 4).replaceAll("\\d", "*")
                + telefone.substring(telefone.length() - 4);
    }

    private static String rotuloSituacao(TcTokenAudienciaSituacao situacao, boolean consultadoNoGateway) {
        if (!consultadoNoGateway) {
            return "Fora do escopo ativo";
        }
        return switch (situacao) {
            case OK -> "OK";
            case PROXIMO_EXPIRAR -> "Próximo de expirar";
            case EXPIRADO -> "Expirado";
            case AUSENTE -> "Sem token";
        };
    }

    private static Double asDouble(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String asString(Object value) {
        return value != null ? String.valueOf(value) : null;
    }

    private static final class AudienciaEntry {
        private final Set<TcTokenAudienciaOrigem> origens = EnumSet.noneOf(TcTokenAudienciaOrigem.class);
        private String githubLogin;
    }

    private record ConsultaGatewayLote(boolean gatewayOnline, Map<String, Map<String, Object>> porTelefone) {}
}
