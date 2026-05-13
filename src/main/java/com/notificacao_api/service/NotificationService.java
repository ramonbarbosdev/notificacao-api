package com.notificacao_api.service;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.notificacao_api.dto.notification.EnviarNotificacaoRequisicao;
import com.notificacao_api.dto.notification.EnviarNotificacaoResposta;
import com.notificacao_api.enums.CanalNotificacao;
import com.notificacao_api.enums.StatusNotificacao;
import com.notificacao_api.model.Notification;
import com.notificacao_api.model.NotificationProviderConfig;
import com.notificacao_api.repository.NotificationProviderConfigRepository;
import com.notificacao_api.repository.NotificationRepository;
import com.notificacao_api.service.provider.NotificationProvider;

@Service
public class NotificationService {

    private final TenantContextService tenantContextService;
    private final NotificationRepository notificationRepository;
    private final NotificationProviderConfigRepository configRepository;
    private final List<NotificationProvider> providers;

    public NotificationService(
            TenantContextService tenantContextService,
            NotificationRepository notificationRepository,
            NotificationProviderConfigRepository configRepository,
            List<NotificationProvider> providers) {
        this.tenantContextService = tenantContextService;
        this.notificationRepository = notificationRepository;
        this.configRepository = configRepository;
        this.providers = providers;
    }

    @Transactional
    public EnviarNotificacaoResposta enviar(EnviarNotificacaoRequisicao requisicao) {
        Long idOrganizacao = tenantContextService.idOrganizacaoObrigatoria();
        CanalNotificacao canal = requisicao.canal();

        NotificationProviderConfig config = configRepository
                .findFirstByIdOrganizacaoAndCanalAndAtivoTrue(idOrganizacao, canal)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Configuracao ativa nao encontrada para o canal " + canal));

        Notification notification = criarNotificacao(idOrganizacao, requisicao);
        notification = notificationRepository.save(notification);

        try {
            providerDoCanal(canal).enviar(notification, config);
            notification.setStatus(StatusNotificacao.ENVIADO);
            notification.setErro(null);
        } catch (RuntimeException ex) {
            notification.setStatus(StatusNotificacao.ERRO);
            notification.setErro(ex.getMessage());
        }

        notification = notificationRepository.save(notification);

        return new EnviarNotificacaoResposta(
                notification.getStatus() == StatusNotificacao.ENVIADO,
                notification.getIdNotificacao(),
                notification.getCanal(),
                notification.getStatus(),
                notification.getErro());
    }

    private Notification criarNotificacao(Long idOrganizacao, EnviarNotificacaoRequisicao requisicao) {
        Notification notification = new Notification();
        notification.setIdOrganizacao(idOrganizacao);
        notification.setCanal(requisicao.canal());
        notification.setDestinatario(requisicao.destinatario());
        notification.setAssunto(requisicao.assunto());
        notification.setMensagem(requisicao.mensagem());
        notification.setStatus(StatusNotificacao.PENDENTE);
        return notification;
    }

    private NotificationProvider providerDoCanal(CanalNotificacao canal) {
        return providers.stream()
                .filter(provider -> provider.getCanal() == canal)
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Provider nao implementado para o canal " + canal));
    }
}
