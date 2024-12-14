package com.example.handler;

import com.example.http.HttpRequest;
import com.example.http.HttpResponse;
import com.example.http.HttpStatus;
import com.example.http.Mime;
import com.example.http.header.HeaderConstants;
import com.example.processor.Triggerable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;

public class ResourceHandlerAdapter<T, R> implements HttpHandlerAdapter<T, R> {
    private static final Logger log = LoggerFactory.getLogger(ResourceHandlerAdapter.class);

    private static final String STATIC_PATH = "static/";

    @Override
    public void handle(HttpRequest request, HttpResponse response, Triggerable<T, R> triggerable) throws IOException {
        String filePath = request.getPath().getBasePath();
        ClassLoader classLoader = ResourceHandlerAdapter.class.getClassLoader();

        try (InputStream inputStream = getResourceStream(classLoader, filePath)) {
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
        response.setStatus(HttpStatus.OK);
        response.getHttpHeaders()
                .addHeader(HeaderConstants.CONTENT_TYPE, mime.getType());
    }

    private InputStream getResourceStream(ClassLoader classLoader, String filePath) {
        String pathPart = filePath.substring(filePath.lastIndexOf('/') + 1);

        if (pathPart.contains(".")) {
            InputStream resourceStream = classLoader.getResourceAsStream(STATIC_PATH + filePath);
            if (resourceStream == null) {
                throw new IllegalArgumentException("파일을 찾을 수 없습니다.");
            }
            return resourceStream;
        }

        if (!filePath.endsWith("/")) {
            filePath += "/";
        }

        filePath += "index.html";
        InputStream resourceStream = classLoader.getResourceAsStream(STATIC_PATH + filePath);
        if (resourceStream == null) {
            throw new IllegalArgumentException("index.html 파일을 찾을 수 없습니다.");
        }

        return resourceStream;
    }
}
