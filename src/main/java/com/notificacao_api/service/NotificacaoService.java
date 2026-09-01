package com.notificacao_api.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.notificacao_api.dto.notificacao.EnviarNotificacaoLoteRequisicao;
import com.notificacao_api.dto.notificacao.EnviarNotificacaoLoteResposta;
import com.notificacao_api.dto.notificacao.EnviarNotificacaoRequisicao;
import com.notificacao_api.dto.notificacao.EnviarNotificacaoResposta;
import com.notificacao_api.dto.notificacao.FilaNotificacaoResponseDTO;
import com.notificacao_api.dto.notificacao.FilaResumoResponseDTO;
import com.notificacao_api.dto.notificacao.NotificacaoFilaFilter;
import com.notificacao_api.dto.notificacao.StatusEnvioOrganizacaoResponse;
import com.notificacao_api.enums.CanalNotificacao;
import com.notificacao_api.service.queue.FilaNotificacaoService;
import com.notificacao_api.service.queue.StatusEnvioOrganizacaoService;

@Service
public class NotificacaoService {

    private final FilaNotificacaoService filaService;
    private final StatusEnvioOrganizacaoService statusEnvioOrganizacaoService;

    public NotificacaoService(
            FilaNotificacaoService filaService,
            StatusEnvioOrganizacaoService statusEnvioOrganizacaoService) {
        this.filaService = filaService;
        this.statusEnvioOrganizacaoService = statusEnvioOrganizacaoService;
    }

    public EnviarNotificacaoResposta enviar(EnviarNotificacaoRequisicao requisicao) {
        return filaService.enfileirar(requisicao);
    }

    public EnviarNotificacaoLoteResposta enviarLote(EnviarNotificacaoLoteRequisicao requisicao) {
        return filaService.enfileirarLote(requisicao);
    }

    public Page<FilaNotificacaoResponseDTO> listarFila(
            NotificacaoFilaFilter filter,
            Pageable pageable) {

        return filaService.listarFila(filter, pageable);
    }

    public FilaResumoResponseDTO resumoFila() {
        return filaService.resumoFila();
    }

    public StatusEnvioOrganizacaoResponse consultarStatusEnvio(CanalNotificacao canal) {
        return statusEnvioOrganizacaoService.consultar(canal);
    }

    public FilaNotificacaoResponseDTO reenviarDaOrganizacao(Long idNotificacao) {
        return filaService.reenviarManualDaOrganizacao(idNotificacao);
    }

    public FilaNotificacaoResponseDTO obterDaOrganizacao(Long idNotificacao) {
        return filaService.obterDaOrganizacao(idNotificacao);
    }
}
