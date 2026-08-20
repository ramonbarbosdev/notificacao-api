package com.notificacao_api.dto.whatsapp;

import com.notificacao_api.enums.CanalNotificacao;

public record ProvisionarConfigWhatsappResposta(
        boolean sucesso,
        Long idOrganizacao,
        CanalNotificacao canal,
        String provedor,
        boolean criada,
        boolean reativada) {

    public static ProvisionarConfigWhatsappResposta ok(
            Long idOrganizacao,
            CanalNotificacao canal,
            String provedor,
            boolean criada,
            boolean reativada) {
        return new ProvisionarConfigWhatsappResposta(true, idOrganizacao, canal, provedor, criada, reativada);
    }
}
