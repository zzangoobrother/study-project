package com.example.api;

import com.example.webserver.http.HttpMethod;
import com.example.webserver.http.HttpVersion;
import com.example.webserver.http.Path;
import com.example.webserver.http.header.HttpHeaders;

import java.io.ByteArrayInputStream;
import java.util.Optional;

public interface Request {
    HttpMethod getMethod();

    Path getPath();

    HttpVersion getVersion();

    HttpHeaders getHeaders();

    ByteArrayInputStream getBody();

    Optional<Object> getAttributes(String key);

    void setAttributes(String key, Object value);
}
