package com.example.processor;

import com.example.http.HttpResponse;
import com.example.http.HttpResponseSerializer;

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
        outputStream.write(httpResponseSerializer.buildHttpResponse(httpResponse));
        outputStream.flush();;
    }
}
