package myjunit.mvc.part1.http;

import java.util.HashMap;
import java.util.Map;

public class HttpSession {
    private Map<String, Object> session = new HashMap<>();

    private String id;

    public HttpSession(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public void setAttribute(String name, Object value) {
        session.put(name, value);
    }

    public Object getAttribute(String name) {
        return session.get(name);
    }

    public void removeAttribute(String name) {
        session.remove(name);
    }

    public void invalidate() {
        HttpSessions.remove(id);
    }
}
