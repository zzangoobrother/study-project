package com.example.http;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class HttpResponseSerializer {

    public byte[] buildHttpResponse(HttpResponse httpResponse) throws IOException {
        HttpVersion httpVersion = httpResponse.getHttpVersion();
        HttpStatus httpStatus = httpResponse.getHttpStatus();

        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

        String responseLine = String.format("%s, %d, %s%s",
                httpVersion.getVersion(),
                httpStatus.getStatusCode(),
                httpStatus.getStatusMessage(),
                System.lineSeparator());

        byteArrayOutputStream.write(responseLine.getBytes(StandardCharsets.UTF_8));

        for (Map.Entry<String, String> headerEntry : httpResponse.getHttpHeaders().getValues()) {
            String header = String.format("%s: %s%s",
                    headerEntry.getKey(),
                    headerEntry.getValue(),
                    System.lineSeparator());

            byteArrayOutputStream.write(header.getBytes(StandardCharsets.UTF_8));
        }

        byteArrayOutputStream.write(System.lineSeparator().getBytes(StandardCharsets.UTF_8));
        byteArrayOutputStream.write(httpResponse.getBody().toByteArray());

        return byteArrayOutputStream.toByteArray();
    }
}
