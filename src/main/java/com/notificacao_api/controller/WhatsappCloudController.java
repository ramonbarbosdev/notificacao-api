package com.notificacao_api.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.notificacao_api.config.MetaAppProperties;
import com.notificacao_api.dto.whatsapp.WhatsappConfigResponse;
import com.notificacao_api.dto.whatsapp.WhatsappEmbeddedSignupCallbackRequest;
import com.notificacao_api.dto.whatsapp.WhatsappEmbeddedSignupConfigResponse;
import com.notificacao_api.service.whatsapp.WhatsappConfigurationService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/app/whatsapp-cloud")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class WhatsappCloudController {

    private static final String WEBHOOK_META_PATH = "/webhooks/whatsapp/meta";

    private final WhatsappConfigurationService configurationService;
    private final MetaAppProperties metaAppProperties;

    public WhatsappCloudController(
            WhatsappConfigurationService configurationService,
            MetaAppProperties metaAppProperties) {
        this.configurationService = configurationService;
        this.metaAppProperties = metaAppProperties;
    }

    @GetMapping("/embedded-signup/config")
    public WhatsappEmbeddedSignupConfigResponse embeddedSignupConfig() {
        return new WhatsappEmbeddedSignupConfigResponse(
                metaAppProperties.embeddedSignupHabilitado(),
                metaAppProperties.id(),
                metaAppProperties.embeddedSignupConfigId(),
                WEBHOOK_META_PATH);
    }

    @PostMapping("/embedded-signup/callback")
    public ResponseEntity<WhatsappConfigResponse> concluirEmbeddedSignup(
            @Valid @RequestBody WhatsappEmbeddedSignupCallbackRequest request) {
        return ResponseEntity.ok(configurationService.concluirEmbeddedSignup(request));
    }
}
