package com.notificacao_api.service.queue;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.notificacao_api.enums.StatusNotificacao;
import com.notificacao_api.model.Notificacao;
import com.notificacao_api.model.ConfiguracaoProvedorNotificacao;
import com.notificacao_api.repository.ConfiguracaoProvedorNotificacaoRepository;
import com.notificacao_api.service.BloqueioAutomaticoContatoService;
import com.notificacao_api.service.provedor.ProvedorNotificacao;
import com.notificacao_api.service.provedor.ExcecaoEnvioProvedor;

@Component
public class ProcessadorFilaNotificacao {

    private final FilaNotificacaoService filaService;
    private final ProtecaoNotificacaoService protecaoService;
    private final SegurancaOperacionalWhatsappService segurancaService;
    private final BloqueioAutomaticoContatoService bloqueioAutomaticoContatoService;
    private final ConfiguracaoProvedorNotificacaoRepository configuracaoRepository;
    private final List<ProvedorNotificacao> provedores;

    public ProcessadorFilaNotificacao(
            FilaNotificacaoService filaService,
            ProtecaoNotificacaoService protecaoService,
            SegurancaOperacionalWhatsappService segurancaService,
            BloqueioAutomaticoContatoService bloqueioAutomaticoContatoService,
            ConfiguracaoProvedorNotificacaoRepository configuracaoRepository,
            List<ProvedorNotificacao> provedores) {
        this.filaService = filaService;
        this.protecaoService = protecaoService;
        this.segurancaService = segurancaService;
        this.bloqueioAutomaticoContatoService = bloqueioAutomaticoContatoService;
        this.configuracaoRepository = configuracaoRepository;
        this.provedores = provedores;
    }

    @Scheduled(fixedDelayString = "${notificacao.protecao.intervalo-agendador-millis:5000}")
    public void processarPendentes() {
        List<Notificacao> pendentes = filaService.buscarPendentesParaProcessar();

        for (Notificacao item : pendentes) {
            if (!filaService.marcarProcessando(item.getIdNotificacao())) {
                continue;
            }

            Notificacao notificacao = filaService.carregar(item.getIdNotificacao());
            processar(notificacao);
        }
    }

    private void processar(Notificacao notificacao) {
        DecisaoProtecaoNotificacao decisao = protecaoService.avaliar(notificacao);
        if (!decisao.permitida()) {
            filaService.reagendar(notificacao, decisao.tentarNovamenteEm(), decisao.motivo());
            return;
        }

        try {
            Thread.sleep(protecaoService.delayAleatorioMillis(notificacao.getIdOrganizacao()));
            ProvedorNotificacao provedor = provedorDoCanal(notificacao);
            ConfiguracaoProvedorNotificacao configuracao = configuracaoRepository
                    .findFirstByIdOrganizacaoAndCanalAndAtivoTrue(notificacao.getIdOrganizacao(), notificacao.getCanal())
                    .orElseThrow(() -> new IllegalStateException(
                            "Configuracao ativa nao encontrada para o canal " + notificacao.getCanal()));

            provedor.enviar(notificacao, configuracao);
            filaService.marcarEnviada(notificacao, configuracao.getProvedor());
            LocalDateTime proximoEnvioApos = protecaoService.agora()
                    .plusSeconds(protecaoService.propriedades().delayMinimoSegundos());
            segurancaService.registrarSucesso(notificacao, proximoEnvioApos);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            filaService.reagendar(notificacao, protecaoService.agora().plusMinutes(1), "Processamento interrompido.");
        } catch (ExcecaoEnvioProvedor ex) {
            tratarFalhaEnvio(notificacao, ex.getMessage(), ex.getClassificacao());
        } catch (RuntimeException ex) {
            ClassificacaoErroEnvio classificacao = ClassificacaoErroEnvio.classificar(ex.getMessage());
            tratarFalhaEnvio(notificacao, ex.getMessage(), classificacao);
        }
    }

    private void tratarFalhaEnvio(Notificacao notificacao, String erro, ClassificacaoErroEnvio classificacao) {
        Notificacao atual = filaService.carregar(notificacao.getIdNotificacao());
        if (atual.getStatus() == StatusNotificacao.CANCELADA) {
            return;
        }

        filaService.marcarFalha(notificacao, erro, classificacao.reenviavel());
        if (classificacao.contaFalhaSessao() && !classificacao.restricaoContatoWhatsapp()) {
            segurancaService.registrarFalha(notificacao, erro);
        }

        Notificacao atualizada = filaService.carregar(notificacao.getIdNotificacao());
        if (atualizada.getStatus() == StatusNotificacao.FALHOU) {
            bloqueioAutomaticoContatoService.avaliarAposFalhaDefinitiva(atualizada, erro, classificacao);
        }
    }

    private ProvedorNotificacao provedorDoCanal(Notificacao notificacao) {
        return provedores.stream()
                .filter(provedor -> provedor.getCanal() == notificacao.getCanal())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "provedor nao implementado para o canal " + notificacao.getCanal()));
    }
}
