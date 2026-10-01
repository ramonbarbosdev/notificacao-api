package com.notificacao_api.service.github;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.notificacao_api.dto.integracao.GithubKanbanMovimentacaoWebhookPayload;
import com.notificacao_api.dto.integracao.GithubKanbanWebhookPessoa;
import com.notificacao_api.enums.GithubKanbanWebhookModoEnvio;
import com.notificacao_api.model.github.GithubKanbanWebhookEventoPendente;
import com.notificacao_api.model.github.OrganizacaoGithubIntegracao;
import com.notificacao_api.repository.OrganizacaoGithubIntegracaoRepository;
import com.notificacao_api.service.github.OrganizacaoGithubKanbanMovimentacaoWebhookService.ConfiguracaoKanbanMovimentacaoWebhook;

@Service
public class GithubKanbanWebhookLoteAgendadoService {

    private static final Logger log = LoggerFactory.getLogger(GithubKanbanWebhookLoteAgendadoService.class);
    private static final DateTimeFormatter ISO_INSTANT = DateTimeFormatter.ISO_INSTANT;

    private final GithubKanbanWebhookFilaService filaService;
    private final GithubKanbanMovimentacaoWebhookService webhookService;
    private final GithubKanbanWebhookNomeEnriquecimentoService nomeEnriquecimentoService;
    private final OrganizacaoGithubIntegracaoRepository integracaoRepository;
    private final OrganizacaoGithubKanbanMovimentacaoWebhookService configService;

    public GithubKanbanWebhookLoteAgendadoService(
            GithubKanbanWebhookFilaService filaService,
            GithubKanbanMovimentacaoWebhookService webhookService,
            GithubKanbanWebhookNomeEnriquecimentoService nomeEnriquecimentoService,
            OrganizacaoGithubIntegracaoRepository integracaoRepository,
            OrganizacaoGithubKanbanMovimentacaoWebhookService configService) {
        this.filaService = filaService;
        this.webhookService = webhookService;
        this.nomeEnriquecimentoService = nomeEnriquecimentoService;
        this.integracaoRepository = integracaoRepository;
        this.configService = configService;
    }

    @Scheduled(fixedDelayString = "${notificacao.github.kanban-webhook.flush-intervalo-millis:60000}")
    public void processarLotesPendentes() {
        for (Long idOrganizacao : filaService.listarOrganizacoesComPendencias()) {
            try {
                flushOrganizacao(idOrganizacao);
            } catch (Exception ex) {
                log.warn("Flush webhook kanban falhou org={} motivo={}", idOrganizacao, ex.getMessage());
            }
        }
    }

    @Transactional
    public void flushOrganizacao(Long idOrganizacao) {
        OrganizacaoGithubIntegracao integracao = integracaoRepository
                .findByIdOrganizacaoForUpdate(idOrganizacao)
                .orElse(null);
        if (integracao == null) {
            return;
        }
        ConfiguracaoKanbanMovimentacaoWebhook config = configService.resolver(integracao);
        if (!config.prontaParaEnvio() || config.modoEnvio() != GithubKanbanWebhookModoEnvio.LOTE) {
            return;
        }
        if (!deveEnviarAgora(integracao, config.intervaloMinutos())) {
            return;
        }

        List<GithubKanbanWebhookEventoPendente> pendentes = filaService.listarPendentes(idOrganizacao);
        if (pendentes.isEmpty()) {
            return;
        }

        List<GithubKanbanMovimentacaoWebhookPayload> movimentacoes = new ArrayList<>();
        Map<String, String> cacheNomes = new HashMap<>();
        Set<String> loginsGraphql = new HashSet<>();
        for (GithubKanbanWebhookEventoPendente pendente : pendentes) {
            GithubKanbanMovimentacaoWebhookPayload item = filaService.deserializar(pendente.getDsPayloadJson());
            movimentacoes.add(item);
            if (item.movidoPorDetalhe() != null) {
                loginsGraphql.addAll(nomeEnriquecimentoService.loginsSemNomeLegivel(List.of(item.movidoPorDetalhe())));
            }
            if (item.responsaveisDetalhe() != null) {
                loginsGraphql.addAll(nomeEnriquecimentoService.loginsSemNomeLegivel(item.responsaveisDetalhe()));
            }
        }

        nomeEnriquecimentoService.preencherNomesViaGraphql(idOrganizacao, integracao, loginsGraphql, cacheNomes);
        List<GithubKanbanMovimentacaoWebhookPayload> enriquecidas = new ArrayList<>();
        for (GithubKanbanMovimentacaoWebhookPayload item : movimentacoes) {
            enriquecidas.add(aplicarCacheNomes(item, cacheNomes));
        }

        LocalDateTime inicio = pendentes.get(0).getDtOcorrido();
        LocalDateTime fim = pendentes.get(pendentes.size() - 1).getDtOcorrido();
        boolean enviado = webhookService.enviarLote(idOrganizacao, config, enriquecidas, inicio, fim);
        if (!enviado) {
            return;
        }

        List<Long> ids = pendentes.stream().map(GithubKanbanWebhookEventoPendente::getId).toList();
        filaService.removerEnviados(ids);
        integracao.setDtGithubKanbanWebhookUltimoFlush(LocalDateTime.now());
        integracaoRepository.save(integracao);
        log.info("Webhook kanban lote enviado org={} itens={}", idOrganizacao, enriquecidas.size());
    }

    private boolean deveEnviarAgora(OrganizacaoGithubIntegracao integracao, int intervaloMinutos) {
        int minutos = Math.max(5, Math.min(intervaloMinutos, 1440));
        LocalDateTime ultimo = integracao.getDtGithubKanbanWebhookUltimoFlush();
        if (ultimo == null) {
            return true;
        }
        return !ultimo.plusMinutes(minutos).isAfter(LocalDateTime.now());
    }

    private GithubKanbanMovimentacaoWebhookPayload aplicarCacheNomes(
            GithubKanbanMovimentacaoWebhookPayload item, Map<String, String> cacheNomes) {
        GithubKanbanWebhookPessoa movido = item.movidoPorDetalhe();
        if (movido != null && StringUtils.hasText(movido.login())) {
            String nome = cacheNomes.getOrDefault(movido.login().toLowerCase(), movido.nome());
            movido = new GithubKanbanWebhookPessoa(movido.login(), nome);
        }
        List<GithubKanbanWebhookPessoa> responsaveis = item.responsaveisDetalhe();
        if (responsaveis != null) {
            List<GithubKanbanWebhookPessoa> atualizados = new ArrayList<>();
            for (GithubKanbanWebhookPessoa pessoa : responsaveis) {
                if (pessoa == null || !StringUtils.hasText(pessoa.login())) {
                    continue;
                }
                String nome = cacheNomes.getOrDefault(pessoa.login().toLowerCase(), pessoa.nome());
                atualizados.add(new GithubKanbanWebhookPessoa(pessoa.login(), nome));
            }
            responsaveis = atualizados.isEmpty() ? null : atualizados;
        }
        return new GithubKanbanMovimentacaoWebhookPayload(
                item.repo(),
                item.tipo(),
                item.numero(),
                item.titulo(),
                item.url(),
                item.de(),
                item.para(),
                item.movidoPor(),
                movido,
                item.responsaveis(),
                responsaveis,
                item.labels(),
                item.prioridade(),
                item.targetDate(),
                item.ocorridoEm());
    }

    static String formatarInstant(LocalDateTime dt) {
        if (dt == null) {
            return ISO_INSTANT.format(Instant.now().atOffset(ZoneOffset.UTC));
        }
        return ISO_INSTANT.format(dt.atOffset(ZoneOffset.UTC));
    }
}
