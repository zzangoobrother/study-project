package com.example.http;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class HttpResponseSerializerTest {

    @Test
    void HttpResponse_객체를_byte_배열로_변환한다() throws IOException {
        HttpResponseSerializer httpResponseSerializer = new HttpResponseSerializer();
        String body = "<html><body>Hello World!</body></html>";
        byte[] bytes = body.getBytes();

        HttpResponse httpResponse = new HttpResponse(HttpVersion.HTTP_1_1);
        httpResponse.getHttpHeaders().addHeader("Content-Type", "text/html");
        httpResponse.getBody().write(bytes);

        byte[] result = httpResponseSerializer.buildHttpResponse(httpResponse);

        String resultStr = new String(result);
        assertThat(resultStr)
                .isNotNull()
                .contains("Content-Type: text/html")
                .contains(body);
    }

    @Test
    void HttpResponse_객체를_byte_배열로_변환한다2() throws IOException {
        HttpResponseSerializer httpResponseSerializer = new HttpResponseSerializer();
        String path = "/index.html";

        HttpResponse httpResponse = HttpResponse.notFoundOf(path);

        byte[] result = httpResponseSerializer.buildHttpResponse(httpResponse);

        String resultStr = new String(result);

        assertThat(resultStr)
                .isNotNull()
                .contains("HTTP/1.1 404 Not Found")
                .contains("Content-Type: text/html; charset=UTF-8")
                .contains("<html><body><h1>404 Not Found " + path + "</h1></body></html>");
    }
}
