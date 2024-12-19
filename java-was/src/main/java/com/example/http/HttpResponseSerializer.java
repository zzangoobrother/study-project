package com.example.http;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

public class HttpResponseSerializer {

    public byte[] buildHttpResponse(HttpResponse httpResponse) throws IOException {
        HttpVersion httpVersion = httpResponse.getHttpVersion();
        HttpStatus httpStatus = httpResponse.getHttpStatus();

        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

        String responseLine = String.format("%s %d %s%s",
                httpVersion.getVersion(),
                httpStatus.getStatusCode(),
                httpStatus.getStatusMessage(),
                System.lineSeparator());

        byteArrayOutputStream.write(responseLine.getBytes());

        for (Map.Entry<String, List<String>> headerEntry : httpResponse.getHttpHeaders().getValues()) {
            String key = headerEntry.getKey();
            List<String> values = headerEntry.getValue();
            String value = String.join("; ", values);

            String header = String.format("%s: %s%s",
                    key,
                    value,
                    System.lineSeparator());

            byteArrayOutputStream.write(header.getBytes());
        }

        byteArrayOutputStream.write(System.lineSeparator().getBytes());
        byteArrayOutputStream.write(httpResponse.getBody().toByteArray());

        return byteArrayOutputStream.toByteArray();
    }
}
