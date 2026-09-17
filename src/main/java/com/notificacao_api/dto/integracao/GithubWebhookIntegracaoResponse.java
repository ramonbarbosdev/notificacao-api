package com.notificacao_api.dto.integracao;

import java.util.List;

public record GithubWebhookIntegracaoResponse(
        boolean featureHabilitada,
        String webhookUrlTemplate,
        String instrucaoWebhookSecret,
        String instrucaoResponsaveis,
        String fraseAtivacaoWhatsapp,
        String linkWhatsappAtivacao,
        boolean whatsappOrigemConectado,
        String templateAssuntoPadrao,
        String templateMensagemPadrao,
        List<String> variaveisTemplateWhatsapp,
        List<GithubWebhookTemplateVariavelResponse> variaveisTemplateDetalhadas,
        List<GithubWebhookTemplateCenarioResponse> cenariosPreview) {
}
