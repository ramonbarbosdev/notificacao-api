package com.notificacao_api.service.provedor;

import org.springframework.stereotype.Component;

import com.notificacao_api.enums.CanalNotificacao;
import com.notificacao_api.enums.WhatsappProvedorEnvio;
import com.notificacao_api.model.ConfiguracaoProvedorNotificacao;
import com.notificacao_api.model.Notificacao;
import com.notificacao_api.service.queue.ClassificacaoErroEnvio;
import com.notificacao_api.service.whatsapp.WhatsappConfigurationService;
import com.notificacao_api.service.whatsapp.provider.MetaCloudWhatsappProvider;
import com.notificacao_api.service.whatsapp.provider.ResultadoEnvioWhatsapp;
import com.notificacao_api.service.whatsapp.provider.WhatsappEnvioProvider;
import com.notificacao_api.service.whatsapp.provider.WhatsappProviderFactory;

@Component
public class ProvedorWhatsApp implements ProvedorNotificacao {

    public static final String AVISO_ACK_NAO_PROPAGOU =
            "WhatsApp aceitou o envio (id da mensagem gerado), mas o recibo (ACK) nao propagou. "
                    + "A mensagem pode nao ter chegado ao contato. Peça para ele enviar a primeira "
                    + "mensagem para este WhatsApp ou confirme manualmente no aparelho.";

    private final WhatsappProviderFactory providerFactory;
    private final WhatsappConfigurationService configurationService;
    private final MetaCloudWhatsappProvider metaCloudWhatsappProvider;

    public ProvedorWhatsApp(
            WhatsappProviderFactory providerFactory,
            WhatsappConfigurationService configurationService,
            MetaCloudWhatsappProvider metaCloudWhatsappProvider) {
        this.providerFactory = providerFactory;
        this.configurationService = configurationService;
        this.metaCloudWhatsappProvider = metaCloudWhatsappProvider;
    }

    @Override
    public CanalNotificacao getCanal() {
        return CanalNotificacao.WHATSAPP;
    }

    @Override
    public ResultadoEnvioProvedor enviar(Notificacao notificacao, ConfiguracaoProvedorNotificacao configuracao) {
        Long idOrganizacao = notificacao.getIdOrganizacao();
        ResultadoEnvioWhatsapp resultado;

        if (configurationService.provedorAtivo(idOrganizacao) == WhatsappProvedorEnvio.META_CLOUD) {
            resultado = metaCloudWhatsappProvider.enviarNotificacao(idOrganizacao, notificacao);
        } else {
            WhatsappEnvioProvider provider = providerFactory.resolver(idOrganizacao);
            resultado = provider.sendText(
                    idOrganizacao,
                    notificacao.getDestinatario(),
                    notificacao.getMensagem(),
                    notificacao);
        }

        if (resultado.externalMessageId() == null && resultado.erro() != null) {
            throw new ExcecaoEnvioProvedor(resultado.erro(), ClassificacaoErroEnvio.classificar(resultado.erro()));
        }

        if (!resultado.confirmado() && AVISO_ACK_NAO_PROPAGOU.equals(resultado.erro())) {
            return ResultadoEnvioProvedor.enviadoSemConfirmacaoEntrega(AVISO_ACK_NAO_PROPAGOU);
        }

        if (!resultado.confirmado() && resultado.erro() != null) {
            throw new ExcecaoEnvioProvedor(resultado.erro(), ClassificacaoErroEnvio.classificar(resultado.erro()));
        }

        return ResultadoEnvioProvedor.confirmado();
    }
}
