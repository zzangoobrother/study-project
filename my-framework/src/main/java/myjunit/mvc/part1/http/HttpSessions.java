package myjunit.mvc.part1.http;

import java.util.HashMap;
import java.util.Map;

public class HttpSessions {
    public static final String SESSION_ID_NAME = "JSESSIONID";

    private static Map<String, HttpSession> httpSessions = new HashMap<>();

    static HttpSession getHttpSession(String id) {
        HttpSession httpSession = httpSessions.get(id);

        if (httpSession == null) {
            httpSession = new HttpSession(id);
            httpSessions.put(id, httpSession);
            return httpSession;
        }

        return httpSession;
    }

    static void remove(String id) {
        httpSessions.remove(id);
    }
}
