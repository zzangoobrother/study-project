package myjunit.mvc.part1.http;

import java.util.HashMap;
import java.util.Map;

public class HttpHeaders {
    private Map<String, String> headers = new HashMap<>();

    void add(String header) {
        String[] splitHeaders = header.split(":");
        headers.put(splitHeaders[0], splitHeaders[1].trim());
    }

    String getHeader(String name) {
        return headers.get(name);
    }

    int getContentLength() {
        return Integer.parseInt(headers.getOrDefault("Content-Length", "0"));
    }
}
