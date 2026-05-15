package com.notificacao_api.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfiguracao implements WebSocketMessageBrokerConfigurer {

    private static final String[] ORIGENS_DEV = {
            "http://localhost:4200",
            "http://127.0.0.1:4200",
            "http://localhost:5173",
            "http://127.0.0.1:5173",
            "https://notificacao.ramoncode.com.br"
    };

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app-ws");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(ORIGENS_DEV);

        registry.addEndpoint("/ws-sockjs")
                .setAllowedOriginPatterns(ORIGENS_DEV)
                .withSockJS();
    }
}
