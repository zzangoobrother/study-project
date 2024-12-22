package com.example.api;

import com.example.webserver.http.HttpStatus;
import com.example.webserver.http.HttpVersion;
import com.example.webserver.http.header.HttpHeaders;

import java.io.ByteArrayOutputStream;

public interface Response {
    HttpVersion getHttpVersion();

    HttpStatus getHttpStatus();

    HttpHeaders getHttpHeaders();

    ByteArrayOutputStream getBody();

    void setHeader(String key, String value);

    void setStatus(HttpStatus httpStatus);
}
