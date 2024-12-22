package com.example.application.domain.images.handler;

import com.example.application.handler.HttpHandler;
import com.example.application.processor.Triggerable;
import com.example.webserver.http.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;

public class ImageResourceHandler implements HttpHandler<String, Void> {

    private static final String BASE_PATH = System.getProperty("user.home") + "/uploads";

    @Override
    public void handle(HttpRequest request, HttpResponse response, Triggerable<String, Void> triggerable) throws Exception {
        Path path = request.getPath();
        String filename = path.getSegments().get(1);

        String relativePath = BASE_PATH + "/" + filename;
        File file = new File(relativePath);

        if (file.exists() && !file.isDirectory()) {
            try (FileInputStream fis = new FileInputStream(file);
                OutputStream os = response.getBody()) {
                response.setStatus(HttpStatus.OK);
                Mime mime = Mime.ofFilePath(filename);
                response.setHttpHeaders("Content-Type", mime.getType());

                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    os.write(buffer, 0, bytesRead);
                }
            }
        } else {
            response.setStatus(HttpStatus.NOT_FOUND);
            response.setHttpHeaders("Content-Type", "text/plain");
            try (OutputStream os = response.getBody()) {
                os.write("File not found".getBytes());
            }
        }
    }
}
