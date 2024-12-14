package com.example.http;

import com.example.http.header.HttpHeaders;

import java.util.Map;

public class HttpRequest {
    private final HttpMethod method;
    private final Path path;
    private final HttpVersion version;
    private final HttpHeaders httpHeaders;
    private final String body;

    public HttpRequest(String method, String path, String version, Map<String, String> httpHeaders, String body) {
        this.method = HttpMethod.of(method);
        this.path = Path.of(path);
        this.version = HttpVersion.of(version);
        this.httpHeaders = HttpHeaders.of(httpHeaders);
        this.body = body;
    }

    public HttpMethod getMethod() {
        return method;
    }

    public Path getPath() {
        return path;
    }

    public HttpVersion getVersion() {
        return version;
    }

    public HttpHeaders getHttpHeaders() {
        return httpHeaders;
    }

    public String getBody() {
        return body;
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
        private Map<String, String> headers;
        private String body;

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

        public Builder headers(Map<String, String> headers) {
            this.headers = headers;
            return this;
        }

        public Builder body(String body) {
            this.body = body;
            return this;
        }

        public HttpRequest build() {
            return new HttpRequest(method, path, version, headers, body);
        }
    }
}
