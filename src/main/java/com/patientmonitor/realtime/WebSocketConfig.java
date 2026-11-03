package com.patientmonitor.realtime;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.support.HttpSessionHandshakeInterceptor;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final MonitorWebSocketHandler handler;

    public WebSocketConfig(MonitorWebSocketHandler handler) {
        this.handler = handler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // 로그아웃 시 소켓을 닫을 수 있도록 HTTP 세션 id 를 WebSocket 세션에 복사해 둔다
        registry.addHandler(handler, "/ws/monitor")
                .addInterceptors(new HttpSessionHandshakeInterceptor());
    }
}
