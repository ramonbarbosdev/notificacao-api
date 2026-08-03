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
            "http://localhost:*",
            "http://127.0.0.1:*",
            "https://notificacao.ramoncode.com.br"
    };

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app-ws");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // WebSocket STOMP nativo (cliente Angular com frames STOMP)
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(ORIGENS_DEV);

        // Fallback SockJS para navegadores/proxies restritivos
        registry.addEndpoint("/ws-sockjs")
                .setAllowedOriginPatterns(ORIGENS_DEV)
                .withSockJS();
    }
}
