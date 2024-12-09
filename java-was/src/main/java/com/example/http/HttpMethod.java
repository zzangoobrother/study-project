package com.example.http;

public enum HttpMethod {
    GET,
    POST,
    PUT,
    DELETE
    ;

    public static HttpMethod of(String method) {
        if (method == null) {
            throw new IllegalArgumentException("HTTP Method cannot be null");
        }

        for (HttpMethod httpMethod : values()) {
            if (httpMethod.name().equalsIgnoreCase(method)) {
                return httpMethod;
            }
        }

        throw new IllegalArgumentException("Invalid HTTP Method : " + method);
    }
}
