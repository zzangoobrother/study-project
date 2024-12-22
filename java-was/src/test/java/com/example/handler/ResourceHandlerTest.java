package com.example.handler;

import com.example.application.handler.StaticResourceHandler;
import com.example.webserver.http.HttpRequest;
import com.example.webserver.http.HttpResponse;
import com.example.webserver.http.HttpVersion;
import com.example.application.processor.Triggerable;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResourceHandlerTest {

    @Test
    void static_파일을_읽어온다() throws IOException {
        StaticResourceHandler<Void, Void> resourceHandlerAdapter = new StaticResourceHandler<>();
        String filePath = "readStaticFileOf.txt";
        HttpRequest request = createGetResourceRequest(filePath);
        HttpResponse response = new HttpResponse(HttpVersion.HTTP_1_1);
        Triggerable<Void, Void> triggerable = o -> null;

        resourceHandlerAdapter.handle(request, response, triggerable);

        String result = response.getBody().toString();

        assertThat(result).contains("example");
    }

    @Test
    void 없는_static_파일을_읽어올_때_예외를_던진다() {
        StaticResourceHandler<Void, Void> resourceHandlerAdapter = new StaticResourceHandler<>();
        String filePath = "invalid.txt";
        HttpRequest request = createGetResourceRequest(filePath);

        HttpResponse response = new HttpResponse(HttpVersion.HTTP_1_1);
        Triggerable<Void, Void> triggerable = o -> null;

        assertThatThrownBy(() -> resourceHandlerAdapter.handle(request, response, triggerable))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("파일을 찾을 수 없습니다.");
    }

    private HttpRequest createGetResourceRequest(String path) {
        return HttpRequest.builder()
                .headers(Map.of("Host", List.of("localhost:8080")))
                .path(path)
                .method("GET")
                .version("HTTP/1.1")
                .build();
    }
}
