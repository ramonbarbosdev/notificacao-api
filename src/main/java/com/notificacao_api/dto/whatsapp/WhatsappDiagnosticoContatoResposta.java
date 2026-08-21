package com.notificacao_api.dto.whatsapp;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record WhatsappDiagnosticoContatoResposta(
        Boolean sucesso,
        String idOrganizacao,
        String erro,
        String telefoneInformado,
        String telefoneNormalizado,
        Boolean sessaoConectada,
        String statusSessao,
        Boolean conversa,
        Boolean prontoParaEnvio,
        String orientacao,
        WhatsappDiagnosticoWhatsappSecao whatsapp,
        WhatsappDiagnosticoTcTokenSecao tctoken,
        WhatsappDiagnosticoInboundSecao inbound,
        List<WhatsappDiagnosticoChecklistItemDTO> checklist) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record WhatsappDiagnosticoWhatsappSecao(
            Boolean existe,
            String jidRetornado,
            String telefoneValidado) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record WhatsappDiagnosticoTcTokenSecao(
            Boolean presente,
            String jidComToken,
            String estrategia) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record WhatsappDiagnosticoInboundSecao(
            Boolean recebida,
            String telefone,
            String jid,
            String recebidaEm,
            String tipo,
            String preview,
            String orientacao) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record WhatsappDiagnosticoChecklistItemDTO(
            String id,
            Boolean ok,
            String rotulo) {
    }
}
