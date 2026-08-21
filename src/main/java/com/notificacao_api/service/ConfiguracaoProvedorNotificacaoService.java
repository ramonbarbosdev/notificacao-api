package com.notificacao_api.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.notificacao_api.dto.whatsapp.ProvisionarConfigWhatsappResposta;
import com.notificacao_api.enums.CanalNotificacao;
import com.notificacao_api.model.ConfiguracaoProvedorNotificacao;
import com.notificacao_api.repository.ConfiguracaoProvedorNotificacaoRepository;

@Service
public class ConfiguracaoProvedorNotificacaoService {

    public static final String PROVEDOR_WHATSAPP_GATEWAY = "WHATSAPP_GATEWAY";
    public static final String PROVEDOR_META_CLOUD = "META_CLOUD";

    private final ConfiguracaoProvedorNotificacaoRepository repository;

    public ConfiguracaoProvedorNotificacaoService(ConfiguracaoProvedorNotificacaoRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public ProvisionarConfigWhatsappResposta garantirWhatsappAtivo(Long idOrganizacao) {
        return repository.findFirstByIdOrganizacaoAndCanal(idOrganizacao, CanalNotificacao.WHATSAPP)
                .map(config -> reativarSeNecessario(idOrganizacao, config))
                .orElseGet(() -> criarWhatsapp(idOrganizacao));
    }

    private ProvisionarConfigWhatsappResposta reativarSeNecessario(
            Long idOrganizacao,
            ConfiguracaoProvedorNotificacao config) {
        if (Boolean.TRUE.equals(config.getAtivo())) {
            return ProvisionarConfigWhatsappResposta.ok(
                    idOrganizacao,
                    CanalNotificacao.WHATSAPP,
                    config.getProvedor(),
                    false,
                    false);
        }

        config.setAtivo(true);
        repository.save(config);
        return ProvisionarConfigWhatsappResposta.ok(
                idOrganizacao,
                CanalNotificacao.WHATSAPP,
                config.getProvedor(),
                false,
                true);
    }

    private ProvisionarConfigWhatsappResposta criarWhatsapp(Long idOrganizacao) {
        ConfiguracaoProvedorNotificacao config = new ConfiguracaoProvedorNotificacao();
        config.setIdOrganizacao(idOrganizacao);
        config.setCanal(CanalNotificacao.WHATSAPP);
        config.setProvedor(PROVEDOR_WHATSAPP_GATEWAY);
        config.setAtivo(true);
        config.setConfiguracoes("{}");
        repository.save(config);

        return ProvisionarConfigWhatsappResposta.ok(
                idOrganizacao,
                CanalNotificacao.WHATSAPP,
                PROVEDOR_WHATSAPP_GATEWAY,
                true,
                false);
    }
}
