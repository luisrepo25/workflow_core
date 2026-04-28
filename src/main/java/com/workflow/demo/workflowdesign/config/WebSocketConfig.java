package com.workflow.demo.workflowdesign.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Configuración de WebSocket y STOMP para edición colaborativa en tiempo real
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Habilitar broker de mensajes simple
        config.enableSimpleBroker("/topic", "/queue");
        
        // Prefijo para mensajes enviados desde clientes
        config.setApplicationDestinationPrefixes("/app");
        
        // Configurar usuario destino para mensajes privados
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Endpoint WebSocket para conexión
        registry.addEndpoint("/ws/workflow")
            .setAllowedOrigins("http://localhost:4200", "http://localhost:3000", "http://localhost:8080")
            .withSockJS(); // Fallback a SockJS si WebSocket no está disponible
    }
}
