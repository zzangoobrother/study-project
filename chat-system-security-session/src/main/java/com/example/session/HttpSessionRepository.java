package com.example.session;

import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class HttpSessionRepository implements HttpSessionListener {

    private static final Logger log = LoggerFactory.getLogger(HttpSessionRepository.class);
    private final Map<String, HttpSession> sessions = new ConcurrentHashMap<>();

    public HttpSession findById(String sessionId) {
        log.info("Session find : {}", sessionId);
        return sessions.get(sessionId);
    }

    @Override
    public void sessionCreated(HttpSessionEvent se) {
        sessions.put(se.getSession().getId(), se.getSession());
        log.info("Session created : {}", se.getSession().getId());
    }

    @Override
    public void sessionDestroyed(HttpSessionEvent se) {
        sessions.remove(se.getSession().getId());
        log.info("Session destroyed : {}", se.getSession().getId());
    }
}
