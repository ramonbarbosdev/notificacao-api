package com.notificacao_api.service.whatsapp;

import java.time.LocalDateTime;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import com.notificacao_api.dto.whatsapp.WhatsappConexaoEvento;

@Service
public class WhatsappConexaoWebSocketService {

    private static final String TOPICO_ORGANIZACAO = "/topic/whatsapp/organizacao/";

    private final SimpMessagingTemplate messagingTemplate;

    public WhatsappConexaoWebSocketService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void publicar(
            Long idOrganizacao,
            String tipo,
            String status,
            Boolean podeConectar,
            Long segundosRestantes,
            String mensagem) {
        messagingTemplate.convertAndSend(
                TOPICO_ORGANIZACAO + idOrganizacao,
                new WhatsappConexaoEvento(
                        idOrganizacao,
                        tipo,
                        status,
                        podeConectar,
                        segundosRestantes,
                        mensagem,
                        LocalDateTime.now()));
    }
}
