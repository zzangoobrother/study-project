package com.example.global.session;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class WebSocketSessionManager {

    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    public List<WebSocketSession> getSessions() {
        return sessions.values().stream().toList();
    }

    public void storeSession(WebSocketSession webSocketSession) {
        sessions.put(webSocketSession.getId(), webSocketSession);
    }

    public void terminateSession(String sessionId) {
        try {
            WebSocketSession webSocketSession = sessions.remove(sessionId);
            if (webSocketSession != null) {
                webSocketSession.close();
            }
        } catch (Exception ex) {
            log.error("WebSocketSession close fail, sessionId : {}", sessionId);
        }
    }
}
