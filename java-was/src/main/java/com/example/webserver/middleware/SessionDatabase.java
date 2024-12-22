package com.example.webserver.middleware;

import com.example.webserver.http.Session;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SessionDatabase {

    private static final Map<String, Session> registry = new ConcurrentHashMap<>();

    public static Session save(Long userPk) {
        Session session = new Session(userPk, LocalDateTime.now(), 3600);
        registry.put(session.getSessionId(), session);
        return session;
    }

    protected static void clear() {
        registry.clear();
    }

    public static Session find(String key) {
        return registry.get(key);
    }

    public static void delete(String key) {
        registry.remove(key);
    }

    public static boolean containsKey(String key) {
        return registry.containsKey(key);
    }
}
