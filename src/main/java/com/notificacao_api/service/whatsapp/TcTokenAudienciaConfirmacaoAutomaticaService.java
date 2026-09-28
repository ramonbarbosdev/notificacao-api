package com.notificacao_api.service.whatsapp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.notificacao_api.dto.notificacao.EnviarNotificacaoResposta;
import com.notificacao_api.dto.whatsapp.TcTokenAudienciaLinhaResponse;
import com.notificacao_api.dto.whatsapp.TcTokenAudienciaScanResponse;
import com.notificacao_api.service.OrganizacaoConfiguracaoService;

@Service
public class TcTokenAudienciaConfirmacaoAutomaticaService {

    private static final Logger log = LoggerFactory.getLogger(TcTokenAudienciaConfirmacaoAutomaticaService.class);

    private final TcTokenAudienciaMonitorService monitorService;
    private final TcTokenAudienciaReativacaoService reativacaoService;
    private final OrganizacaoConfiguracaoService organizacaoConfiguracaoService;
    private final int maxPorExecucao;

    public TcTokenAudienciaConfirmacaoAutomaticaService(
            TcTokenAudienciaMonitorService monitorService,
            TcTokenAudienciaReativacaoService reativacaoService,
            OrganizacaoConfiguracaoService organizacaoConfiguracaoService,
            @Value("${notificacao.tctoken.confirmacao-automatica.max-por-org-por-execucao:10}") int maxPorExecucao) {
        this.monitorService = monitorService;
        this.reativacaoService = reativacaoService;
        this.organizacaoConfiguracaoService = organizacaoConfiguracaoService;
        this.maxPorExecucao = Math.max(1, maxPorExecucao);
    }

    public ResultadoProcessamento processarOrganizacao(Long idOrganizacao) {
        if (!organizacaoConfiguracaoService.tctokenConfirmacaoAutomaticaHabilitado(idOrganizacao)) {
            return ResultadoProcessamento.ignorado("Motor desligado");
        }
        if (!organizacaoConfiguracaoService.envioMensagensHabilitado(idOrganizacao)) {
            return ResultadoProcessamento.ignorado("Envio de mensagens desligado");
        }

        int diasAntes = organizacaoConfiguracaoService.tctokenConfirmacaoAutomaticaDiasAntes(idOrganizacao);
        TcTokenAudienciaScanResponse scan = monitorService.executarScan(idOrganizacao);

        int enfileirados = 0;
        int ignorados = 0;
        int erros = 0;

        for (TcTokenAudienciaLinhaResponse linha : scan.linhas()) {
            if (enfileirados >= maxPorExecucao) {
                break;
            }
            if (!reativacaoService.linhaElegivelConfirmacaoAutomatica(idOrganizacao, linha, diasAntes)) {
                ignorados++;
                continue;
            }
            try {
                EnviarNotificacaoResposta resposta = reativacaoService.pedirConfirmacaoAutomatica(idOrganizacao, linha);
                if (Boolean.TRUE.equals(resposta.sucesso())) {
                    enfileirados++;
                } else {
                    erros++;
                }
            } catch (ResponseStatusException ex) {
                if (ex.getStatusCode().value() == 429) {
                    ignorados++;
                } else {
                    erros++;
                    log.debug(
                            "Confirmacao automatica org={} telefone={}: {}",
                            idOrganizacao,
                            linha.telefone(),
                            ex.getReason());
                }
            } catch (Exception ex) {
                erros++;
                log.warn(
                        "Falha confirmacao automatica org={} telefone={}: {}",
                        idOrganizacao,
                        linha.telefone(),
                        ex.getMessage());
            }
        }

        if (enfileirados > 0 || erros > 0) {
            log.info(
                    "Confirmacao automatica tctoken org={}: enfileirados={} ignorados={} erros={} (limite {} dias)",
                    idOrganizacao,
                    enfileirados,
                    ignorados,
                    erros,
                    diasAntes);
        }

        return new ResultadoProcessamento(false, enfileirados, ignorados, erros, null);
    }

    public record ResultadoProcessamento(
            boolean ignoradoOrganizacao,
            int enfileirados,
            int ignorados,
            int erros,
            String motivoIgnorado) {

        static ResultadoProcessamento ignorado(String motivo) {
            return new ResultadoProcessamento(true, 0, 0, 0, motivo);
        }
    }
}
