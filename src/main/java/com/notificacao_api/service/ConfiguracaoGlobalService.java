package com.notificacao_api.service;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.notificacao_api.dto.configuracao.ConfiguracaoGlobalRequest;
import com.notificacao_api.dto.configuracao.ConfiguracaoGlobalResponse;
import com.notificacao_api.model.ConfiguracaoGlobal;
import com.notificacao_api.repository.ConfiguracaoGlobalRepository;

@Service
public class ConfiguracaoGlobalService {

    private final ConfiguracaoGlobalRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final AuditoriaEventoService auditoriaService;

    public ConfiguracaoGlobalService(
            ConfiguracaoGlobalRepository repository,
            PasswordEncoder passwordEncoder,
            AuditoriaEventoService auditoriaService) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.auditoriaService = auditoriaService;
    }

    @Transactional
    public ConfiguracaoGlobalResponse buscar() {
        return toResponse(obterOuCriar());
    }

    @Transactional
    public ConfiguracaoGlobalResponse atualizar(ConfiguracaoGlobalRequest request) {
        ConfiguracaoGlobal config = obterOuCriar();
        ConfiguracaoGlobalResponse antes = toResponse(config);

        config.setNmPlataforma(valor(request.nmPlataforma(), config.getNmPlataforma()));
        config.setNmDominioPrincipal(request.nmDominioPrincipal());
        config.setNmEmailSuporte(request.nmEmailSuporte());
        config.setNmEmailAlertas(request.nmEmailAlertas());
        config.setDsSmtpHost(request.dsSmtpHost());
        config.setNuSmtpPorta(request.nuSmtpPorta());
        config.setNmSmtpUsuario(request.nmSmtpUsuario());
        if (request.dsSmtpSenha() != null && !request.dsSmtpSenha().isBlank()) {
            config.setDsSmtpSenhaCriptografada(request.dsSmtpSenha());
        }
        config.setNuTimezonePadrao(request.nuTimezonePadrao());
        config.setFlWhatsappProviderPadrao(bool(request.flWhatsappProviderPadrao(), config.getFlWhatsappProviderPadrao()));
        config.setFlApiPublicaHabilitada(bool(request.flApiPublicaHabilitada(), config.getFlApiPublicaHabilitada()));
        config.setFlTemplatesHabilitado(bool(request.flTemplatesHabilitado(), config.getFlTemplatesHabilitado()));
        config.setFlWebhooksHabilitado(bool(request.flWebhooksHabilitado(), config.getFlWebhooksHabilitado()));
        config.setFlTelegramHabilitado(bool(request.flTelegramHabilitado(), config.getFlTelegramHabilitado()));
        config.setFlEmailHabilitado(bool(request.flEmailHabilitado(), config.getFlEmailHabilitado()));

        ConfiguracaoGlobal salvo = repository.save(config);
        ConfiguracaoGlobalResponse depois = toResponse(salvo);
        auditoriaService.registrar(null, "CONFIGURACAO_GLOBAL", "ATUALIZAR", "Configuracao global alterada.", antes, depois);
        return depois;
    }

    private ConfiguracaoGlobal obterOuCriar() {
        return repository.findAll().stream()
                .findFirst()
                .orElseGet(() -> repository.save(new ConfiguracaoGlobal()));
    }

    private String valor(String novo, String atual) {
        return novo == null || novo.isBlank() ? atual : novo;
    }

    private Boolean bool(Boolean novo, Boolean atual) {
        return novo == null ? atual : novo;
    }

    private ConfiguracaoGlobalResponse toResponse(ConfiguracaoGlobal config) {
        if (config == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Configuracao global nao encontrada.");
        }
        return new ConfiguracaoGlobalResponse(
                config.getIdConfiguracaoGlobal(),
                config.getNmPlataforma(),
                config.getNmDominioPrincipal(),
                config.getNmEmailSuporte(),
                config.getNmEmailAlertas(),
                config.getDsSmtpHost(),
                config.getNuSmtpPorta(),
                config.getNmSmtpUsuario(),
                config.getDsSmtpSenhaCriptografada() != null,
                config.getNuTimezonePadrao(),
                config.getFlWhatsappProviderPadrao(),
                config.getFlApiPublicaHabilitada(),
                config.getFlTemplatesHabilitado(),
                config.getFlWebhooksHabilitado(),
                config.getFlTelegramHabilitado(),
                config.getFlEmailHabilitado(),
                config.getDtCriacao(),
                config.getDtAtualizacao());
    }
}
