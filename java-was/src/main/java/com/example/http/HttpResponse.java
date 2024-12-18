package com.example.http;

import com.example.http.header.HeaderConstants;
import com.example.http.header.HttpHeaders;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

public class HttpResponse {

    private final HttpVersion httpVersion;
    private HttpStatus httpStatus;
    private HttpHeaders httpHeaders;
    private final ByteArrayOutputStream body;

    public HttpResponse(HttpVersion httpVersion) {
        this.httpVersion = validateHttpVersion(httpVersion);
        this.httpStatus = HttpStatus.INITIAL_STATUS;
        this.httpHeaders = HttpHeaders.emptyHeader();
        this.body = new ByteArrayOutputStream();
    }

    public HttpResponse(HttpVersion httpVersion, HttpStatus httpStatus, Map<String, List<String>> headers, ByteArrayOutputStream body) {
        this.httpVersion = validateHttpVersion(httpVersion);
        this.httpStatus = validateHttpStatus(httpStatus);
        this.httpHeaders = HttpHeaders.of(validateHeaders(headers));
        this.body = body;
    }

    public static HttpResponse unauthorizedOf(String basePath) {
        byte[] responseBytes = ("<html><body><h1>401 Unauthorized " + basePath + "</h1></body></html>").getBytes(StandardCharsets.UTF_8);
        HttpResponse httpResponse = new HttpResponse(HttpVersion.HTTP_1_1);
        httpResponse.httpStatus = HttpStatus.UNAUTHORIZED;
        httpResponse.httpHeaders = HttpHeaders.of(Map.of(HeaderConstants.CONTENT_TYPE, List.of("text/html", "charset=UTF-8")));
        httpResponse.setHttpHeaders(HeaderConstants.SET_COOKIE, "sid=; Path=/ ; Max-Age=0; HttpOnly");
        httpResponse.body.write(responseBytes, 0, responseBytes.length);

        return httpResponse;
    }

    private HttpVersion validateHttpVersion(HttpVersion httpVersion) {
        if (httpVersion == null) {
            throw new IllegalArgumentException("HttpVersion이 존재하지 않습니다.");
        }

        return httpVersion;
    }

    private HttpStatus validateHttpStatus(HttpStatus httpStatus) {
        if (httpStatus == null) {
            throw new IllegalArgumentException("HttpStatus가 존재하지 않습니다.");
        }

        return httpStatus;
    }

    private Map<String, List<String>> validateHeaders(Map<String, List<String>> headers) {
        if (headers == null) {
            throw new IllegalArgumentException("Headers가 존재하지 않습니다.");
        }

        return headers;
    }

    public static HttpResponse notFoundOf(String path) {
        byte[] responseBytes = ("<html><body><h1>404 Not Found " + path + "</h1></body></html>").getBytes(StandardCharsets.UTF_8);

        HttpResponse httpResponse = HttpResponse.builder()
                .httpVersion(HttpVersion.HTTP_1_1)
                .httpStatus(HttpStatus.NOT_FOUND)
                .headers(Map.of("Content-Type", List.of("text/html; charset=UTF-8")))
                .body(new ByteArrayOutputStream())
                .build();

        httpResponse.body.write(responseBytes, 0, responseBytes.length);

        return httpResponse;
    }

    public static HttpResponse internalServerErrorOf(String path) {
        byte[] responseBytes = ("<html><body><h1>500 Internal Server Error " + path + "</h1></body></html>").getBytes(StandardCharsets.UTF_8);

        HttpResponse httpResponse = HttpResponse.builder()
                .httpVersion(HttpVersion.HTTP_1_1)
                .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                .headers(Map.of("Content-Type", List.of("text/html; charset=UTF-8")))
                .body(new ByteArrayOutputStream())
                .build();

        httpResponse.body.write(responseBytes, 0, responseBytes.length);

        return httpResponse;
    }

    public HttpVersion getHttpVersion() {
        return httpVersion;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public HttpHeaders getHttpHeaders() {
        return httpHeaders;
    }

    public ByteArrayOutputStream getBody() {
        return body;
    }

    public void setStatus(HttpStatus httpStatus) {
        this.httpStatus = httpStatus;
    }

    public void setHttpHeaders(String key, String value) {
        httpHeaders.addHeader(key, value);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private HttpVersion httpVersion;
        private HttpStatus httpStatus;
        private Map<String, List<String>> headers;
        private ByteArrayOutputStream body;

        public Builder httpVersion(HttpVersion httpVersion) {
            this.httpVersion = httpVersion;
            return this;
        }

        public Builder httpStatus(HttpStatus httpStatus) {
            this.httpStatus = httpStatus;
            return this;
        }

        public Builder headers(Map<String, List<String>> headers) {
            this.headers = headers;
            return this;
        }

        public Builder body(ByteArrayOutputStream body) {
            this.body = body;
            return this;
        }

        public HttpResponse build() {
            return new HttpResponse(httpVersion, httpStatus, headers, body);
        }
    }
}
