package com.example.session;

import com.example.dto.domain.UserId;
import com.example.dto.websocket.outbound.BaseMessage;
import com.example.util.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class WebSocketSessionManager {

    private static final Logger log = LoggerFactory.getLogger(WebSocketSessionManager.class);
    private final Map<UserId, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final JsonUtil jsonUtil;

    public WebSocketSessionManager(JsonUtil jsonUtil) {
        this.jsonUtil = jsonUtil;
    }

    public List<WebSocketSession> getSessions() {
        return sessions.values().stream().toList();
    }

    public WebSocketSession getSession(UserId userId) {
        return sessions.get(userId);
    }

    public void putSession(UserId userId, WebSocketSession webSocketSession) {
        log.info("Store Session : {}", webSocketSession.getId());
        sessions.put(userId, webSocketSession);
    }

    public void closeSession(UserId userId) {
        try {
            WebSocketSession webSocketSession = sessions.remove(userId);
            if (webSocketSession != null) {
                log.info("Remove session : {}", userId);
                webSocketSession.close();
                log.info("Close session : {}", userId);
            }
        } catch (Exception ex) {
            log.error("Failed WebSocketSession close. userId : {}", userId);
        }
    }

    public void sendMessage(WebSocketSession session, BaseMessage message) {
        jsonUtil.toJson(message).ifPresent(msg -> {
            try {
                session.sendMessage(new TextMessage(msg));
                log.info("send message : {} to {}", msg, session.getId());
            } catch (Exception ex) {
                log.error("메시지 전송 실패 cause : {}", ex.getMessage());
            }
        });
    }
}
