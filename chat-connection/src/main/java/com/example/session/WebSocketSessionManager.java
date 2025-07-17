package com.example.session;

import com.example.dto.domain.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class WebSocketSessionManager {

    private static final Logger log = LoggerFactory.getLogger(WebSocketSessionManager.class);
    private final Map<UserId, WebSocketSession> sessions = new ConcurrentHashMap<>();

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

    public void sendMessage(WebSocketSession session, String message) throws IOException {
        try {
            session.sendMessage(new TextMessage(message));
            log.info("send message : {} to {}", message, session.getId());
        } catch (IOException ex) {
            log.error("Send message failed. cause : {}", ex.getMessage());
            throw ex;
        }
    }
}
