package com.example.http;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpResponseTest {

    @Test
    void HttpResponse_객체를_생성한다() throws IOException {
        HttpVersion httpVersion = HttpVersion.HTTP_1_1;

        HttpResponse httpResponse = new HttpResponse(httpVersion);

        assertThat(httpResponse)
                .extracting("httpVersion", "httpStatus")
                .containsAnyOf(httpVersion, HttpStatus.INITIAL_STATUS);
    }

    @Test
    void HttpVersion_없이_HttpResponse_객체를_생성하면_예외가_발생한다() throws IOException {
        HttpVersion httpVersion = null;

        assertThatThrownBy(() -> new HttpResponse(httpVersion))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("HttpVersion이 존재하지 않습니다.");
    }

    @Test
    void HttpStatus_없이_HttpResponse_객체를_생성하면_예외가_발생한다() throws IOException {
        HttpVersion httpVersion = HttpVersion.HTTP_1_1;
        HttpStatus httpStatus = null;
        Map<String, String> headers = Map.of("Content-Type", "text/html; charset=UTF-8");
        String body = "<html><body>Hello World!</body></html>";
        byte[] bytes = body.getBytes();
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        byteArrayOutputStream.write(bytes);

        assertThatThrownBy(() -> new HttpResponse(httpVersion, httpStatus, headers, byteArrayOutputStream))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("HttpStatus가 존재하지 않습니다.");
    }

    @Test
    void headers_없이_HttpResponse_객체를_생성하면_예외가_발생한다() throws IOException {
        HttpVersion httpVersion = HttpVersion.HTTP_1_1;
        HttpStatus httpStatus = HttpStatus.OK;
        Map<String, String> headers = null;
        String body = "<html><body>Hello World!</body></html>";
        byte[] bytes = body.getBytes();
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        byteArrayOutputStream.write(bytes);

        assertThatThrownBy(() -> new HttpResponse(httpVersion, httpStatus, headers, byteArrayOutputStream))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Headers가 존재하지 않습니다.");
    }
}
