package com.example.handler;

import com.example.http.HttpRequest;
import com.example.http.HttpResponse;
import com.example.http.Mime;
import com.example.http.header.HeaderConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;

public class ResourceHandler implements HttpHandler {
    private static final Logger log = LoggerFactory.getLogger(ResourceHandler.class);

    private static final String STATIC_PATH = "static/";

    @Override
    public void handle(HttpRequest request, HttpResponse response) throws IOException {
        String filePath = request.getPath().getValue();
        ClassLoader classLoader = ResourceHandler.class.getClassLoader();
        InputStream inputStream = classLoader.getResourceAsStream(STATIC_PATH + filePath);

        if (inputStream == null) {
            throw new IllegalArgumentException("File not found! : static/" + filePath);
        }

        try (inputStream) {
            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                response.getBody().write(buffer, 0, bytesRead);
            }
        } catch (IllegalArgumentException e) {
            log.error("Failed to read file", e);
            throw e;
        }

        Mime mime = Mime.ofFilePath(filePath);
        response.getHttpHeaders()
                .addHeader(HeaderConstants.CONTENT_TYPE, mime.getType());
    }
}
