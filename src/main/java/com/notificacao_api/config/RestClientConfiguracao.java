package com.notificacao_api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfiguracao {

    @Bean
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }
}