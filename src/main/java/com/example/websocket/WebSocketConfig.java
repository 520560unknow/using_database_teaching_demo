package com.example.websocket;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    @Autowired
    private StudentWebSocketHandler studentWebSocketHandler;

    @Autowired
    private TeacherWebSocketHandler teacherWebSocketHandler;

    @Autowired
    private DanmuWebSocketServer danmuWebSocketServer;

    @Autowired
    private VideoUploadWebSocketHandler videoUploadWebSocketHandler;

    @Value("${websocket.allowed-origins:*}")
    private String allowedOrigins;

    @Bean
    @ConditionalOnWebApplication
    public ServletServerContainerFactoryBean createWebSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxTextMessageBufferSize(2097152);
        container.setMaxBinaryMessageBufferSize(2097152);
        return container;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        String[] origins = allowedOrigins.split(",");

        registry.addHandler(studentWebSocketHandler, "/ws/student/{classroomId}")
                .setAllowedOriginPatterns(origins);

        registry.addHandler(teacherWebSocketHandler, "/ws/teacher/{classroomId}")
                .setAllowedOriginPatterns(origins);

        registry.addHandler(danmuWebSocketServer, "/ws/danmu")
                .setAllowedOriginPatterns(origins)
                .withSockJS();

        registry.addHandler(videoUploadWebSocketHandler, "/ws/upload/video")
                .setAllowedOriginPatterns(origins);
    }
}
