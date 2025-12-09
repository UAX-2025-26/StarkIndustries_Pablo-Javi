package com.starkindustries.security.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

// Configura soporte WebSocket con STOMP para mensajería en tiempo real
@Configuration // Indica que esta clase contiene definiciones de beans (@Bean) que serán gestionados por el contenedor de Spring
@EnableWebSocketMessageBroker // Habilita el soporte para WebSocket con un message broker STOMP, permitiendo comunicación bidireccional en tiempo real
public class WebSocketConfiguration implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Broker simple en memoria para destinos que empiezan por /topic o /queue
        config.enableSimpleBroker("/topic", "/queue");
        // Prefijo para mensajes que van del cliente a métodos @MessageMapping
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Endpoint WebSocket principal usado por el frontend (SockJS para fallback)
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }
}
