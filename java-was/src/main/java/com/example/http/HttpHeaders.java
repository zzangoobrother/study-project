package com.example.http;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class HttpHeaders {

    private final Map<String, String> valueMap;

    private HttpHeaders(Map<String, String> valueMap) {
        this.valueMap = new HashMap<>(valueMap);
    }

    public void addHeader(String key, String value) {
        valueMap.put(key, value);
    }

    public static HttpHeaders of(Map<String, String> headers) {
        return new HttpHeaders(headers);
    }

    public String getHeader(String key) {
        String value = valueMap.get(key);
        if (value == null) {
            throw new IllegalArgumentException("Header not found : " + key);
        }

        return value;
    }

    public Set<Map.Entry<String, String>> getValues() {
        return valueMap.entrySet();
    }
}
