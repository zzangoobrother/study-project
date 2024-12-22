package com.example.webserver.processor;

import com.example.webserver.http.HttpResponse;
import com.example.webserver.http.header.HeaderConstants;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;

public class HttpResponseWriter {

    private final HttpResponseSerializer httpResponseSerializer;

    public HttpResponseWriter(HttpResponseSerializer httpResponseSerializer) {
        this.httpResponseSerializer = httpResponseSerializer;
    }

    public void writeResponse(Socket socket, HttpResponse httpResponse) throws IOException {
        OutputStream outputStream = socket.getOutputStream();
        httpResponse.setHttpHeaders(HeaderConstants.CONTENT_LENGTH, String.valueOf(httpResponse.getBody().size()));
        outputStream.write(httpResponseSerializer.buildHttpResponse(httpResponse));
        outputStream.flush();;
    }
}
