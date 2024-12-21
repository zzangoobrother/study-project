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

public class StaticResourceHandler<T, R> implements HttpHandler<T, R> {
    private static final Logger log = LoggerFactory.getLogger(StaticResourceHandler.class);

    private static final String STATIC_PATH = "static";

    @Override
    public void handle(HttpRequest request, HttpResponse response, Triggerable<T, R> triggerable) throws IOException {
        String filePath = request.getPath().getBasePath();
        ClassLoader classLoader = StaticResourceHandler.class.getClassLoader();

        try (InputStream inputStream = getResourceStream(classLoader, filePath)) {
            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                response.getBody().write(buffer, 0, bytesRead);
            }
        } catch (IllegalArgumentException e) {
            response.setStatus(HttpStatus.NOT_FOUND);
            log.error("리소스 파일을 읽는 중 오류 발생 : {}, {}", filePath, e.getMessage());
            throw e;
        } catch (IOException e) {
            response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR);
            log.error("리소스 파일을 읽는 중 IO 오류 발생 : {}, {}", filePath, e.getMessage());
        }

        Mime mime = Mime.ofFilePath(filePath);
        response.setStatus(HttpStatus.OK);
        response.setHttpHeaders(HeaderConstants.CACHE_CONTROL, "public max-age=31536000");

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
