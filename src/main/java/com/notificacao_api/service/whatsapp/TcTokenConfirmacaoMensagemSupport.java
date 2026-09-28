package com.notificacao_api.service.whatsapp;

import org.springframework.util.StringUtils;

import com.notificacao_api.dto.whatsapp.TcTokenAudienciaLinhaResponse;
import com.notificacao_api.enums.TcTokenAudienciaOrigem;

public final class TcTokenConfirmacaoMensagemSupport {

    public static final String PLACEHOLDER_LOGIN = "{login}";
    public static final String PLACEHOLDER_DIAS = "{diasRestantes}";

    private TcTokenConfirmacaoMensagemSupport() {}

    public static String resolverMensagem(
            String mensagemInformadaOverride,
            String templateOrganizacao,
            TcTokenAudienciaLinhaResponse linha,
            boolean modoTeste) {
        String corpo;
        if (StringUtils.hasText(mensagemInformadaOverride)) {
            corpo = mensagemInformadaOverride.trim();
        } else if (StringUtils.hasText(templateOrganizacao)) {
            corpo = aplicarTemplate(templateOrganizacao.trim(), linha);
        } else {
            corpo = TcTokenAudienciaReativacaoService.montarMensagemSistema(linha);
        }
        if (modoTeste) {
            return "[TESTE ADMIN] " + corpo;
        }
        return corpo;
    }

    static String aplicarTemplate(String template, TcTokenAudienciaLinhaResponse linha) {
        String login = resolverLoginPlaceholder(linha);
        String dias = resolverDiasPlaceholder(linha);
        return template.replace(PLACEHOLDER_LOGIN, login).replace(PLACEHOLDER_DIAS, dias);
    }

    static String resolverLoginPlaceholder(TcTokenAudienciaLinhaResponse linha) {
        if (linha == null) {
            return "";
        }
        if (linha.origens() != null
                && linha.origens().contains(TcTokenAudienciaOrigem.GITHUB)
                && linha.nomeExibicao() != null
                && linha.nomeExibicao().startsWith("@")) {
            return linha.nomeExibicao();
        }
        return "";
    }

    static String resolverDiasPlaceholder(TcTokenAudienciaLinhaResponse linha) {
        if (linha == null || linha.expiraEmDias() == null) {
            return "—";
        }
        return String.valueOf(Math.round(linha.expiraEmDias()));
    }
}
