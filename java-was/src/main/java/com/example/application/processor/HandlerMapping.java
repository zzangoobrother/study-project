package com.example.application.processor;

import com.example.application.handler.HttpHandler;
import com.example.webserver.http.HttpMethod;

import java.util.regex.Pattern;

public class HandlerMapping<T, R> {
    private static final Pattern DISALLOWED_SPECIAL_CHARACTERS_PATTERN = Pattern.compile("[!@#$%^&*()+=|<>?\\[\\]~]");

    private final HttpMethod httpMethod;
    private final Pattern pattern;
    private final HttpHandler<T, R> handler;
    private final Triggerable<T, R> triggerable;

    public HandlerMapping(HttpMethod httpMethod, String url, HttpHandler<T, R> handler, Triggerable<T, R> triggerable) {
        this.httpMethod = validateHttpMethod(httpMethod);
        this.pattern = transformUrlToRegexPattern(url);
        this.handler = validateHandler(handler);
        this.triggerable = validateTriggerable(triggerable);
    }

    private HttpMethod validateHttpMethod(HttpMethod httpMethod) {
        if (httpMethod == null) {
            throw new IllegalArgumentException("httpMethod가 null 입니다.");
        }

        return httpMethod;
    }

    private Pattern transformUrlToRegexPattern(String url) {
        if (url == null || url.isEmpty()) {
            throw new IllegalArgumentException("url이 null 이거나 비어 있습니다.");
        }

        if (DISALLOWED_SPECIAL_CHARACTERS_PATTERN.matcher(url).find()) {
            throw new IllegalArgumentException("url에 허용되지 않은 특수 문자가 포함될 수 있습니다.");
        }

        if (url.matches(".*\\{\\s*\\}.*")) {
            throw new IllegalArgumentException("PathVariable이 null이거나 비어있습니다.");
        }

        String regexPattern = url.replaceAll("\\{[^/]+\\}", "([^/]+)");
        regexPattern = "^" + regexPattern + "$";
        return Pattern.compile(regexPattern);
    }

    private HttpHandler<T, R> validateHandler(HttpHandler<T, R> handler) {
        if (handler == null) {
            throw new IllegalArgumentException("handler가 null 입니다.");
        }

        return handler;
    }

    private Triggerable<T, R> validateTriggerable(Triggerable<T, R> triggerable) {
        if (triggerable == null) {
            throw new IllegalArgumentException("triggerable이 null 입니다.");
        }

        return triggerable;
    }

    public boolean matchRequest(HttpMethod httpMethod, String basePath) {
        return this.httpMethod.equals(httpMethod) && pattern.matcher(basePath).matches();
    }

    public Triggerable<T, R> getTriggerable() {
        return triggerable;
    }

    public Pattern getPattern() {
        return pattern;
    }

    public HttpMethod getHttpMethod() {
        return httpMethod;
    }

    public HttpHandler<T, R> getHandler() {
        return handler;
    }
}
