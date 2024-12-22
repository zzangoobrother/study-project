package com.example.webserver.http;

import com.example.api.Request;
import com.example.webserver.http.header.HttpHeaders;

import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class HttpRequest implements Request {
    private final HttpMethod method;
    private final Path path;
    private final HttpVersion version;
    private final HttpHeaders httpHeaders;
    private final ByteArrayInputStream body;
    private final Map<String, Object> attributes;

    public HttpRequest(String method, String path, String version, Map<String, List<String>> httpHeaders, ByteArrayInputStream body) {
        this.method = HttpMethod.of(method);
        this.path = Path.of(path);
        this.version = HttpVersion.of(version);
        this.httpHeaders = HttpHeaders.of(httpHeaders);
        this.body = body;
        this.attributes = new HashMap<>();
    }

    @Override
    public HttpMethod getMethod() {
        return method;
    }

    @Override
    public Path getPath() {
        return path;
    }

    @Override
    public HttpVersion getVersion() {
        return version;
    }

    @Override
    public HttpHeaders getHeaders() {
        return httpHeaders;
    }

    public ByteArrayInputStream getBody() {
        return body;
    }

    @Override
    public Optional<Object> getAttributes(String key) {
        return Optional.ofNullable(attributes.get(key));
    }

    @Override
    public void setAttributes(String key, Object value) {
        validateAttributeKey(key);
    }

    private void validateAttributeKey(String key) {
        if (key == null || key.isEmpty()) {
            throw new IllegalArgumentException("key는 null이거나 빈 문자열일 수 없습니다.");
        }
    }

    private void validateAttributeValue(Object value) {
        if (value == null) {
            throw new IllegalArgumentException("value는 null일 수 없습니다.");
        }
    }

    @Override
    public String toString() {
        return "HttpRequest{" +
                "method=" + method +
                ", path=" + path +
                ", version=" + version +
                ", httpHeaders=" + httpHeaders +
                ", body='" + body + '\'' +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String method;
        private String path;
        private String version;
        private Map<String, List<String>> headers;
        private ByteArrayInputStream  body;

        public Builder method(String method) {
            this.method = method;
            return this;
        }

        public Builder path(String path) {
            this.path = path;
            return this;
        }

        public Builder version(String version) {
            this.version = version;
            return this;
        }

        public Builder headers(Map<String, List<String>> headers) {
            this.headers = headers;
            return this;
        }

        public Builder body(ByteArrayInputStream body) {
            this.body = body;
            return this;
        }

        public HttpRequest build() {
            return new HttpRequest(method, path, version, headers, body);
        }
    }
}
