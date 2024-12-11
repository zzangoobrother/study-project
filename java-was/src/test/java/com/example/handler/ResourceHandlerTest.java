package com.example.handler;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResourceHandlerTest {

    @Test
    void static_파일을_읽어온다() {
        ResourceHandler resourceHandler = new ResourceHandler();
        String filePath = "readStaticFileOf.txt";

        InputStream inputStream = resourceHandler.readFileAsStream(filePath);

        BufferedReader br = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));

        assertThat(br.lines()).contains("example");
    }

    @Test
    void 없는_static_파일을_읽어올_때_예외를_던진다() {
        ResourceHandler resourceHandler = new ResourceHandler();
        String filePath = "invalid.txt";

        assertThatThrownBy(() -> resourceHandler.readFileAsStream(filePath))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("File not found! : static/invalid.txt");
    }
}
