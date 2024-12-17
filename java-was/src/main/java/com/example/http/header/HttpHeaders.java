package com.example.http.header;

import java.util.*;

public class HttpHeaders {

    private final Map<String, List<String>> valueMap;

    private HttpHeaders() {
        this.valueMap = new HashMap<>();
    }

    private HttpHeaders(Map<String, List<String>> valueMap) {
        this.valueMap = new HashMap<>(valueMap);
    }

    public static HttpHeaders emptyHeader() {
        return new HttpHeaders();
    }

    public static HttpHeaders of(Map<String, List<String>> headers) {
        return new HttpHeaders(headers);
    }

    public void addHeader(String key, String value) {
        valueMap.put(key, value);
    }

    public List<String> getHeader(String key) {
        List<String> value = valueMap.get(key);
        if (value == null) {
            throw new IllegalArgumentException("Header not found : " + key);
        }

        return value;
    }

    public Optional<String> getSubValueOfHeader(String key, String subkey) {
        List<String> values = valueMap.get(key);
        if (values == null) {
            return Optional.empty();
        }

        for (String value : values) {
            String[] keyValue = value.split("=");
            if (keyValue[0].equals(subkey)) {
                return Optional.ofNullable(keyValue[1]);
            }
        }

        return Optional.empty();
    }

    public Set<Map.Entry<String, List<String>>> getValues() {
        return valueMap.entrySet();
    }

    @Override
    public String toString() {
        return valueMap + "";
    }
}
