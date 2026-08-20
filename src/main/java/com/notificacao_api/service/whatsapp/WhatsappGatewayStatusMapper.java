package com.notificacao_api.service.whatsapp;

import com.notificacao_api.enums.WhatsappSessionStatus;

public final class WhatsappGatewayStatusMapper {

    private WhatsappGatewayStatusMapper() {
    }

    public static String normalizar(String statusGateway) {
        if (statusGateway == null || statusGateway.isBlank()) {
            return statusGateway;
        }

        return switch (statusGateway.trim().toUpperCase()) {
            case "NOT_STARTED" -> WhatsappSessionStatus.NAO_INICIADO.name();
            case "CONNECTING" -> WhatsappSessionStatus.CONECTANDO.name();
            case "PENDING_QR" -> WhatsappSessionStatus.AGUARDANDO_QR.name();
            case "CONNECTED" -> WhatsappSessionStatus.CONECTADO.name();
            case "DISCONNECTED" -> WhatsappSessionStatus.DESCONECTADO.name();
            case "LOGGED_OUT" -> WhatsappSessionStatus.DESLOGADO.name();
            default -> statusGateway.trim().toUpperCase();
        };
    }

    public static boolean emAndamento(String status) {
        if (status == null || status.isBlank()) {
            return false;
        }

        String normalizado = normalizar(status);
        try {
            WhatsappSessionStatus statusSessao = WhatsappSessionStatus.valueOf(normalizado);
            return statusSessao == WhatsappSessionStatus.CONECTANDO
                    || statusSessao == WhatsappSessionStatus.AGUARDANDO_QR;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }
}
