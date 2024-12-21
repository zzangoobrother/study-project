package com.example.api;

import com.example.http.HttpMethod;
import com.example.http.HttpVersion;
import com.example.http.Path;
import com.example.http.header.HttpHeaders;

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
