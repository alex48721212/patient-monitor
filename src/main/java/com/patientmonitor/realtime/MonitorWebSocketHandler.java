package com.patientmonitor.realtime;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.security.web.session.HttpSessionDestroyedEvent;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.socket.server.support.HttpSessionHandshakeInterceptor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 접속한 대시보드들에게 서버 → 클라이언트 방향으로 이벤트를 밀어준다.
 * 메시지 형식: {"type": "...", "data": ...}
 */
@Component
public class MonitorWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(MonitorWebSocketHandler.class);

    private static final int SEND_TIME_LIMIT_MS = 5_000;
    private static final int BUFFER_SIZE_LIMIT = 512 * 1024;

    private final ObjectMapper objectMapper;
    // WebSocketSession.sendMessage 는 동시 호출이 안전하지 않아 데코레이터로 감싼다
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    public MonitorWebSocketHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.put(session.getId(),
                new ConcurrentWebSocketSessionDecorator(session, SEND_TIME_LIMIT_MS, BUFFER_SIZE_LIMIT));
        log.info("대시보드 접속: {} (현재 {}명)", session.getId(), sessions.size());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session.getId());
        log.info("대시보드 종료: {} (현재 {}명)", session.getId(), sessions.size());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        sessions.remove(session.getId());
    }

    /** 로그아웃·세션 만료 후에는 이미 열려 있던 소켓으로도 환자 데이터를 보내지 않는다 */
    @EventListener
    public void onHttpSessionDestroyed(HttpSessionDestroyedEvent event) {
        sessions.values().stream()
                .filter(session -> event.getId().equals(
                        session.getAttributes().get(HttpSessionHandshakeInterceptor.HTTP_SESSION_ID_ATTR_NAME)))
                .forEach(session -> {
                    sessions.remove(session.getId());
                    try {
                        session.close(CloseStatus.POLICY_VIOLATION);
                    } catch (IOException e) {
                        log.debug("소켓 종료 실패: {}", session.getId());
                    }
                });
    }

    public void broadcast(String type, Object data) {
        if (sessions.isEmpty()) {
            return;
        }
        TextMessage message;
        try {
            message = new TextMessage(objectMapper.writeValueAsString(Map.of("type", type, "data", data)));
        } catch (JsonProcessingException e) {
            log.error("메시지 직렬화 실패 type={}", type, e);
            return;
        }
        sessions.values().forEach(session -> {
            try {
                if (session.isOpen()) {
                    session.sendMessage(message);
                }
            } catch (IOException | RuntimeException e) {
                log.debug("전송 실패, 세션 제거: {}", session.getId());
                sessions.remove(session.getId());
            }
        });
    }

    public int connectedCount() {
        return sessions.size();
    }
}
