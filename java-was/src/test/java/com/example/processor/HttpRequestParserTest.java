package com.example.processor;

import com.example.webserver.http.HttpRequest;
import com.example.webserver.processor.HttpRequestParser;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class HttpRequestParserTest {

    private final HttpRequestParser httpRequestParser = new HttpRequestParser();

    @Test
    void urlencoded된_요청이_오는_경우에는_decode된_body를_HttpRequest에_저장한다() throws IOException {
        HttpRequestParser httpRequestBuilder = new HttpRequestParser();
        String request = """
                POST /user/create HTTP/1.1
                Host: localhost:8080
                Connection: keep-alive
                Content-Length: 93
                Content-Type: application/x-www-form-urlencoded
                
                userId=hong&password=password&name=%EB%B0%95%EC%9E%AC%EC%84%B1&email=hong%40slipp.net
                """.replace("\n", "\r\n");

        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(request.getBytes());

        HttpRequest httpRequest = httpRequestParser.parseRequest(byteArrayInputStream);

        assertThat(new String(httpRequest.getBody().readAllBytes()))
                .contains("userId=hong&password=password&name=박재성&email=hong@slipp.net");
    }

    @Test
    void urlencoded_되어있지_않을_경우_decode_하지_않은_body를_HttpRequest에_저장한다() throws IOException {
        HttpRequestParser httpRequestBuilder = new HttpRequestParser();
        String request = """
                POST /user/create HTTP/1.1
                Host: localhost:8080
                Connection: keep-alive
                Content-Length: 93
                Content-Type: application/json
                
                userId=hong&password=password&name=%EB%B0%95%EC%9E%AC%EC%84%B1&email=hong%40slipp.net
                """.replace("\n", "\r\n");

        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(request.getBytes());

        HttpRequest httpRequest = httpRequestParser.parseRequest(byteArrayInputStream);

        assertThat(new String(httpRequest.getBody().readAllBytes()))
                .contains("userId=hong&password=password&name=%EB%B0%95%EC%9E%AC%EC%84%B1&email=hong%40slipp.net");
    }
}
