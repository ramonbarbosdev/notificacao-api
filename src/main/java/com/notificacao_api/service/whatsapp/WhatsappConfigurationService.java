package com.notificacao_api.service.whatsapp;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.notificacao_api.dto.whatsapp.WhatsappConfigCreateRequest;
import com.notificacao_api.dto.whatsapp.WhatsappConfigResponse;
import com.notificacao_api.dto.whatsapp.WhatsappConfigTestResponse;
import com.notificacao_api.dto.whatsapp.WhatsappConfigUpdateRequest;
import com.notificacao_api.enums.CanalNotificacao;
import com.notificacao_api.enums.WhatsappProvedorEnvio;
import com.notificacao_api.model.ConfiguracaoProvedorNotificacao;
import com.notificacao_api.model.WhatsappConfiguracao;
import com.notificacao_api.model.WhatsappCredencial;
import com.notificacao_api.repository.ConfiguracaoProvedorNotificacaoRepository;
import com.notificacao_api.repository.WhatsappConfiguracaoRepository;
import com.notificacao_api.repository.WhatsappCredencialRepository;
import com.notificacao_api.security.crypto.EncryptionService;
import com.notificacao_api.service.ConfiguracaoProvedorNotificacaoService;
import com.notificacao_api.service.TenantContextService;
import com.notificacao_api.service.whatsapp.provider.MetaGraphApiClient;

@Service
public class WhatsappConfigurationService {

    private final TenantContextService tenantContextService;
    private final WhatsappConfiguracaoRepository configuracaoRepository;
    private final WhatsappCredencialRepository credencialRepository;
    private final ConfiguracaoProvedorNotificacaoRepository provedorRepository;
    private final EncryptionService encryptionService;
    private final MetaGraphApiClient metaGraphApiClient;

    public WhatsappConfigurationService(
            TenantContextService tenantContextService,
            WhatsappConfiguracaoRepository configuracaoRepository,
            WhatsappCredencialRepository credencialRepository,
            ConfiguracaoProvedorNotificacaoRepository provedorRepository,
            EncryptionService encryptionService,
            MetaGraphApiClient metaGraphApiClient) {
        this.tenantContextService = tenantContextService;
        this.configuracaoRepository = configuracaoRepository;
        this.credencialRepository = credencialRepository;
        this.provedorRepository = provedorRepository;
        this.encryptionService = encryptionService;
        this.metaGraphApiClient = metaGraphApiClient;
    }

    @Transactional(readOnly = true)
    public Optional<WhatsappConfigResponse> buscarConfiguracaoAtual() {
        Long idOrganizacao = tenantContextService.idOrganizacaoObrigatoria();
        return configuracaoRepository.findByIdOrganizacao(idOrganizacao).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public boolean metaCloudAtivo(Long idOrganizacao) {
        return configuracaoRepository.findByIdOrganizacao(idOrganizacao)
                .filter(config -> Boolean.TRUE.equals(config.getAtivo()))
                .isPresent();
    }

    @Transactional(readOnly = true)
    public WhatsappProvedorEnvio provedorAtivo(Long idOrganizacao) {
        if (metaCloudAtivo(idOrganizacao)) {
            return WhatsappProvedorEnvio.META_CLOUD;
        }
        return WhatsappProvedorEnvio.WHATSAPP_GATEWAY;
    }

    @Transactional(readOnly = true)
    public ConfiguracaoMetaAtiva obterConfiguracaoMetaAtiva(Long idOrganizacao) {
        WhatsappConfiguracao config = configuracaoRepository.findByIdOrganizacao(idOrganizacao)
                .filter(c -> Boolean.TRUE.equals(c.getAtivo()))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT, "WhatsApp Cloud API nao configurada ou inativa para esta organizacao."));

        WhatsappCredencial credencial = credencialRepository.findByIdConfiguracao(config.getIdConfiguracao())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT, "Credencial WhatsApp Cloud API nao encontrada."));

        String token = encryptionService.decrypt(credencial.getAccessTokenCriptografado());
        return new ConfiguracaoMetaAtiva(
                idOrganizacao,
                config.getPhoneNumberId(),
                config.getWabaId(),
                config.getApiVersion(),
                token);
    }

    @Transactional
    public WhatsappConfigResponse criar(WhatsappConfigCreateRequest request) {
        Long idOrganizacao = tenantContextService.idOrganizacaoObrigatoria();
        if (configuracaoRepository.findByIdOrganizacao(idOrganizacao).isPresent()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Configuracao WhatsApp Cloud API ja existe. Use PUT para atualizar.");
        }

        validarCampos(request.phoneNumberId(), request.accessToken());

        WhatsappConfiguracao config = new WhatsappConfiguracao();
        config.setIdOrganizacao(idOrganizacao);
        config.setProvider(WhatsappProvedorEnvio.META_CLOUD);
        config.setPhoneNumberId(request.phoneNumberId().trim());
        config.setWabaId(trimOrNull(request.wabaId()));
        config.setApiVersion(apiVersionOrDefault(request.apiVersion()));
        config.setAtivo(request.active() == null || request.active());
        configuracaoRepository.save(config);

        WhatsappCredencial credencial = new WhatsappCredencial();
        credencial.setIdConfiguracao(config.getIdConfiguracao());
        credencial.setAccessTokenCriptografado(encryptionService.encrypt(request.accessToken().trim()));
        credencialRepository.save(credencial);

        sincronizarProvedorNotificacao(idOrganizacao, config.getAtivo());
        return toResponse(config);
    }

    @Transactional
    public WhatsappConfigResponse atualizar(WhatsappConfigUpdateRequest request) {
        Long idOrganizacao = tenantContextService.idOrganizacaoObrigatoria();
        WhatsappConfiguracao config = configuracaoRepository.findByIdOrganizacao(idOrganizacao)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Configuracao WhatsApp Cloud API nao encontrada."));

        if (request.phoneNumberId() != null && !request.phoneNumberId().isBlank()) {
            config.setPhoneNumberId(request.phoneNumberId().trim());
        }
        if (request.wabaId() != null) {
            config.setWabaId(trimOrNull(request.wabaId()));
        }
        if (request.apiVersion() != null && !request.apiVersion().isBlank()) {
            config.setApiVersion(request.apiVersion().trim());
        }
        if (request.active() != null) {
            config.setAtivo(request.active());
        }

        if (request.accessToken() != null && !request.accessToken().isBlank()) {
            WhatsappCredencial credencial = credencialRepository.findByIdConfiguracao(config.getIdConfiguracao())
                    .orElseGet(() -> {
                        WhatsappCredencial nova = new WhatsappCredencial();
                        nova.setIdConfiguracao(config.getIdConfiguracao());
                        return nova;
                    });
            credencial.setAccessTokenCriptografado(encryptionService.encrypt(request.accessToken().trim()));
            credencialRepository.save(credencial);
        }

        configuracaoRepository.save(config);
        sincronizarProvedorNotificacao(idOrganizacao, config.getAtivo());
        return toResponse(config);
    }

    @Transactional
    public void desativar() {
        Long idOrganizacao = tenantContextService.idOrganizacaoObrigatoria();
        WhatsappConfiguracao config = configuracaoRepository.findByIdOrganizacao(idOrganizacao)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Configuracao WhatsApp Cloud API nao encontrada."));
        config.setAtivo(false);
        configuracaoRepository.save(config);
        sincronizarProvedorNotificacao(idOrganizacao, false);
    }

    @Transactional
    public WhatsappConfigTestResponse testarConexao() {
        Long idOrganizacao = tenantContextService.idOrganizacaoObrigatoria();
        ConfiguracaoMetaAtiva config = obterConfiguracaoMetaAtiva(idOrganizacao);
        MetaGraphApiClient.TesteConexaoResult resultado = metaGraphApiClient.testarConexao(config);

        configuracaoRepository.findByIdOrganizacao(idOrganizacao).ifPresent(c -> {
            c.setDtUltimoTeste(LocalDateTime.now());
            configuracaoRepository.save(c);
        });

        return new WhatsappConfigTestResponse(resultado.success(), resultado.message());
    }

    @Transactional(readOnly = true)
    public Optional<WhatsappConfiguracao> buscarPorPhoneNumberId(String phoneNumberId) {
        return configuracaoRepository.findByPhoneNumberIdAndAtivoTrue(phoneNumberId);
    }

    private void sincronizarProvedorNotificacao(Long idOrganizacao, boolean metaAtivo) {
        ConfiguracaoProvedorNotificacao provedor = provedorRepository
                .findFirstByIdOrganizacaoAndCanal(idOrganizacao, CanalNotificacao.WHATSAPP)
                .orElseGet(() -> {
                    ConfiguracaoProvedorNotificacao novo = new ConfiguracaoProvedorNotificacao();
                    novo.setIdOrganizacao(idOrganizacao);
                    novo.setCanal(CanalNotificacao.WHATSAPP);
                    novo.setAtivo(true);
                    novo.setConfiguracoes("{}");
                    return novo;
                });

        provedor.setProvedor(metaAtivo
                ? ConfiguracaoProvedorNotificacaoService.PROVEDOR_META_CLOUD
                : ConfiguracaoProvedorNotificacaoService.PROVEDOR_WHATSAPP_GATEWAY);
        provedor.setAtivo(true);
        provedorRepository.save(provedor);
    }

    private WhatsappConfigResponse toResponse(WhatsappConfiguracao config) {
        boolean tokenConfigurado = credencialRepository.findByIdConfiguracao(config.getIdConfiguracao()).isPresent();
        return new WhatsappConfigResponse(
                config.getProvider().name(),
                config.getPhoneNumberId(),
                config.getWabaId(),
                config.getApiVersion(),
                Boolean.TRUE.equals(config.getAtivo()),
                tokenConfigurado,
                config.getDtUltimoTeste());
    }

    private void validarCampos(String phoneNumberId, String accessToken) {
        if (phoneNumberId == null || phoneNumberId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "phoneNumberId e obrigatorio.");
        }
        if (accessToken == null || accessToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "accessToken e obrigatorio.");
        }
    }

    private String apiVersionOrDefault(String apiVersion) {
        if (apiVersion == null || apiVersion.isBlank()) {
            return "v21.0";
        }
        return apiVersion.trim();
    }

    private String trimOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    public record ConfiguracaoMetaAtiva(
            Long idOrganizacao,
            String phoneNumberId,
            String wabaId,
            String apiVersion,
            String accessToken) {
    }
}
